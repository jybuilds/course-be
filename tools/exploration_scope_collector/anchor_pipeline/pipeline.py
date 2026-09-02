from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from .collector import collect_batch
from .io_utils import read_json, write_json
from .lmstudio import LmStudioClient
from .models import SourceSpec, ValidationIssue
from .prompts import evaluation_prompt, normalize_prompt
from .schemas import ANCHOR_LIST_SCHEMA, EVALUATION_SCHEMA
from .validator import deduplicate_anchors, validate_anchor, validate_evaluation


class CourseAnchorPipeline:
    def __init__(
        self,
        *,
        project_dir: Path,
        workspace_dir: Path,
        lm_studio_url: str,
        model: str | None,
        refresh: bool = False,
    ) -> None:
        self.project_dir = project_dir
        self.config_dir = project_dir / "config"
        self.workspace_dir = workspace_dir
        self.client = LmStudioClient(lm_studio_url, model)
        self.refresh = refresh

    def _resolve_city(self, city: str) -> tuple[str, dict[str, Any], dict[str, Any]]:
        index = read_json(self.config_dir / "cities" / "index.json")
        city_key = index.get("cities", {}).get(city)
        if not city_key:
            supported = ", ".join(sorted(index.get("cities", {})))
            raise ValueError(f"지원하지 않는 큰 지역입니다: {city}. 현재 지원: {supported}")
        plan = read_json(self.config_dir / "cities" / f"{city_key}.json")
        registry = read_json(self.config_dir / "source_registry" / f"{city_key}.json")
        return city_key, plan, registry

    def _contract(self) -> dict[str, Any]:
        return read_json(self.config_dir / "contracts" / "backend-recommendation.json")

    def initialize_city(self, city: str) -> Path:
        city_key, plan, registry = self._resolve_city(city)
        runtime_dir = self.workspace_dir / city_key
        runtime_dir.mkdir(parents=True, exist_ok=True)
        manifest_path = runtime_dir / "city-manifest.json"
        write_json(
            manifest_path,
            {
                "cityKey": city_key,
                "parentRegionName": plan["parentRegionName"],
                "collectionRegions": plan["collectionRegions"],
                "sourceRegistryVersion": registry.get("version", 1),
                "createdAt": datetime.now(UTC).isoformat(),
                "note": "collectionRegions는 수집 배치 단위이며 백엔드 regions 테이블 데이터가 아니다.",
            },
        )
        return manifest_path

    def collect(self, city: str) -> list[Path]:
        city_key, plan, registry = self._resolve_city(city)
        self.initialize_city(city)
        raw_dir = self.workspace_dir / city_key / "raw"
        common = [SourceSpec.from_dict(item) for item in registry.get("commonSources", [])]
        region_sources = registry.get("regionSources", {})
        outputs: list[Path] = []

        for region in plan["collectionRegions"]:
            output = raw_dir / f"{region['code'].lower()}.json"
            if output.exists() and not self.refresh:
                outputs.append(output)
                continue
            specific = [SourceSpec.from_dict(item) for item in region_sources.get(region["code"], [])]
            result = collect_batch(region["code"], region["name"], [*common, *specific])
            write_json(
                output,
                {
                    "metadata": {
                        "parentRegionName": plan["parentRegionName"],
                        "collectionRegion": region,
                        "collectedAt": datetime.now(UTC).isoformat(),
                    },
                    **result.to_dict(),
                },
            )
            outputs.append(output)
        return outputs

    @staticmethod
    def _chunks(text: str, max_chars: int = 10_000) -> list[str]:
        if len(text) <= max_chars:
            return [text]
        chunks: list[str] = []
        start = 0
        while start < len(text):
            end = min(start + max_chars, len(text))
            if end < len(text):
                newline = text.rfind("\n", start, end)
                if newline > start + max_chars // 2:
                    end = newline
            chunks.append(text[start:end])
            start = end
        return chunks

    def normalize(self, city: str) -> list[Path]:
        city_key, plan, _ = self._resolve_city(city)
        contract = self._contract()
        raw_dir = self.workspace_dir / city_key / "raw"
        normalized_dir = self.workspace_dir / city_key / "normalized"
        outputs: list[Path] = []

        for region in plan["collectionRegions"]:
            raw_path = raw_dir / f"{region['code'].lower()}.json"
            if not raw_path.exists():
                continue
            output = normalized_dir / f"{region['code'].lower()}.json"
            if output.exists() and not self.refresh:
                outputs.append(output)
                continue

            raw = read_json(raw_path)
            allowed_urls = {source["url"] for source in raw.get("sources", [])}
            generated: list[dict[str, Any]] = []
            issues: list[ValidationIssue] = []
            model_errors: list[str] = []
            for source in raw.get("sources", []):
                for chunk_index, chunk in enumerate(self._chunks(source.get("text", "")), start=1):
                    try:
                        response = self.client.structured(
                            prompt=normalize_prompt(
                                city=plan["parentRegionName"],
                                collection_region=region,
                                source=source,
                                source_text=chunk,
                                existing_anchors=contract.get("existingAnchors", []),
                            ),
                            schema_name="course_anchor_candidates",
                            schema=ANCHOR_LIST_SCHEMA,
                        )
                        for raw_anchor in response.get("anchors", []):
                            anchor, anchor_issues = validate_anchor(
                                raw_anchor,
                                city=plan["parentRegionName"],
                                allowed_urls=allowed_urls,
                                existing_anchors=contract.get("existingAnchors", []),
                            )
                            anchor["collectionRegionCode"] = region["code"]
                            generated.append(anchor)
                            issues.extend(anchor_issues)
                    except RuntimeError as error:
                        model_errors.append(f"{source['url']} chunk {chunk_index}: {error}")

            anchors, duplicate_issues = deduplicate_anchors(generated)
            issues.extend(duplicate_issues)
            write_json(
                output,
                {
                    "metadata": {
                        "parentRegionName": plan["parentRegionName"],
                        "collectionRegion": region,
                        "normalizedAt": datetime.now(UTC).isoformat(),
                        "model": self.client.model,
                    },
                    "anchors": anchors,
                    "validationIssues": [issue.to_dict() for issue in issues],
                    "modelErrors": model_errors,
                },
            )
            outputs.append(output)
        return outputs

    def build_catalogue(self, city: str) -> dict[str, Any]:
        city_key, plan, _ = self._resolve_city(city)
        normalized_dir = self.workspace_dir / city_key / "normalized"
        catalogue_dir = self.workspace_dir / city_key / "catalogue"
        all_anchors: list[dict[str, Any]] = []
        all_issues: list[ValidationIssue] = []
        for region in plan["collectionRegions"]:
            path = normalized_dir / f"{region['code'].lower()}.json"
            if not path.exists():
                continue
            result = read_json(path)
            all_anchors.extend(result.get("anchors", []))
        anchors, duplicate_issues = deduplicate_anchors(all_anchors)
        all_issues.extend(duplicate_issues)
        areas = sorted((item for item in anchors if item.get("type") == "AREA"), key=lambda item: item["name"])
        hubs = sorted((item for item in anchors if item.get("type") == "HUB"), key=lambda item: item["name"])
        reviews = sorted((item for item in anchors if item.get("status") == "REVIEW_NEEDED"), key=lambda item: item.get("name", ""))
        write_json(catalogue_dir / "anchors-area.json", {"parentRegionName": plan["parentRegionName"], "anchors": areas})
        write_json(catalogue_dir / "anchors-hub.json", {"parentRegionName": plan["parentRegionName"], "anchors": hubs})
        write_json(catalogue_dir / "review-needed.json", {"parentRegionName": plan["parentRegionName"], "anchors": reviews})
        return {"cityKey": city_key, "areas": areas, "hubs": hubs, "reviews": reviews, "issues": all_issues}

    def evaluate(self, city: str) -> tuple[Path, Path]:
        city_key, plan, _ = self._resolve_city(city)
        contract = self._contract()
        catalogue = self.build_catalogue(city)
        # Rules are generated for review candidates too; backend-import.json remains ACTIVE-only.
        anchors = [*catalogue["areas"], *catalogue["hubs"]]
        evaluation_dir = self.workspace_dir / city_key / "evaluation"
        activity_rules: list[dict[str, Any]] = []
        preference_rules: list[dict[str, Any]] = []
        evaluations: list[dict[str, Any]] = []
        validation_issues: list[ValidationIssue] = []

        for anchor in anchors:
            output = evaluation_dir / f"{anchor['code'].lower()}.json"
            if output.exists() and not self.refresh:
                evaluated = read_json(output)
            else:
                try:
                    response = self.client.structured(
                        prompt=evaluation_prompt(anchor, contract),
                        schema_name="course_anchor_rule_evaluation",
                        schema=EVALUATION_SCHEMA,
                    )
                    evaluated = {
                        "courseAnchorCode": anchor["code"],
                        "anchorStatus": anchor["status"],
                        "evaluatedAt": datetime.now(UTC).isoformat(),
                        **response,
                    }
                except RuntimeError as error:
                    evaluated = {
                        "courseAnchorCode": anchor["code"],
                        "anchorStatus": anchor["status"],
                        "activityRules": [],
                        "preferenceRules": [],
                        "reviewNotes": [str(error)],
                    }
                write_json(output, evaluated)

            valid_activities, valid_preferences, issues = validate_evaluation(
                evaluated, contract, anchor["code"]
            )
            activity_rules.extend(valid_activities)
            preference_rules.extend(valid_preferences)
            validation_issues.extend(issues)
            evaluations.append(evaluated)

        activity_path = evaluation_dir / "course-anchor-activity-rules.json"
        preference_path = evaluation_dir / "course-anchor-preference-rules.json"
        write_json(activity_path, {"rules": activity_rules})
        write_json(preference_path, {"rules": preference_rules})

        active_anchors = [
            anchor
            for anchor in [*catalogue["areas"], *catalogue["hubs"]]
            if anchor.get("status") == "ACTIVE_CANDIDATE"
        ]
        active_codes = {anchor["code"] for anchor in active_anchors}
        import_path = self.workspace_dir / city_key / "catalogue" / "backend-import.json"
        write_json(
            import_path,
            {
                "metadata": {
                    "parentRegionName": plan["parentRegionName"],
                    "generatedAt": datetime.now(UTC).isoformat(),
                    "contractVersion": contract["version"],
                    "reviewRequired": True,
                },
                "courseAnchors": [
                    {
                        "code": anchor["code"],
                        "type": anchor["type"],
                        "parentRegionName": anchor["parentRegionName"],
                        "name": anchor["name"],
                        "description": anchor["description"],
                        "latitude": anchor["latitude"],
                        "longitude": anchor["longitude"],
                        "radiusMeters": anchor["radiusMeters"],
                        "displayOrder": index,
                        "active": False,
                    }
                    for index, anchor in enumerate(active_anchors, start=1)
                ],
                "courseAnchorActivityRules": [rule for rule in activity_rules if rule["courseAnchorCode"] in active_codes],
                "courseAnchorPreferenceRules": [rule for rule in preference_rules if rule["courseAnchorCode"] in active_codes],
                "validationIssues": [issue.to_dict() for issue in validation_issues],
            },
        )
        return activity_path, preference_path

    def run(self, city: str) -> Path:
        self.collect(city)
        self.normalize(city)
        self.evaluate(city)
        city_key, _, _ = self._resolve_city(city)
        report_path = self.workspace_dir / city_key / "run-report.json"
        catalogue = self.build_catalogue(city)
        write_json(
            report_path,
            {
                "city": city,
                "completedAt": datetime.now(UTC).isoformat(),
                "areaCount": len(catalogue["areas"]),
                "hubCount": len(catalogue["hubs"]),
                "reviewNeededCount": len(catalogue["reviews"]),
                "backendImport": str(self.workspace_dir / city_key / "catalogue" / "backend-import.json"),
            },
        )
        return report_path

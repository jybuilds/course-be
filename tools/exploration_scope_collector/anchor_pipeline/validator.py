from __future__ import annotations

import re
from copy import deepcopy
from typing import Any

from .models import ValidationIssue


CODE_PATTERN = re.compile(r"^[A-Z0-9_]{1,50}$")
ALLOWED_TYPES = {"AREA", "HUB"}
ALLOWED_STATUSES = {"ACTIVE_CANDIDATE", "REVIEW_NEEDED"}
ALLOWED_WEIGHTS = {5, 10, 15, 20, 25}


def normalize_name(value: str) -> str:
    return re.sub(r"[^0-9a-z가-힣]", "", value.casefold())


def _mark_review(anchor: dict[str, Any], reason: str) -> None:
    anchor["status"] = "REVIEW_NEEDED"
    reasons = anchor.setdefault("reviewReasons", [])
    if reason not in reasons:
        reasons.append(reason)


def validate_anchor(
    raw_anchor: dict[str, Any],
    *,
    city: str,
    allowed_urls: set[str],
    existing_anchors: list[dict[str, Any]],
) -> tuple[dict[str, Any], list[ValidationIssue]]:
    anchor = deepcopy(raw_anchor)
    issues: list[ValidationIssue] = []
    name = str(anchor.get("name", "")).strip()
    anchor["name"] = name

    existing_by_name: dict[str, dict[str, Any]] = {}
    for existing in existing_anchors:
        for candidate in [existing.get("name", ""), *existing.get("aliases", [])]:
            if candidate:
                existing_by_name[normalize_name(candidate)] = existing
    existing = existing_by_name.get(normalize_name(name))
    if existing:
        anchor["code"] = existing["code"]

    if not name:
        issues.append(ValidationIssue("INVALID_NAME", "이름이 비어 있습니다."))
        _mark_review(anchor, "이름 확인 필요")
    if anchor.get("type") not in ALLOWED_TYPES:
        issues.append(ValidationIssue("INVALID_TYPE", "AREA 또는 HUB가 아닙니다.", name))
        _mark_review(anchor, "유형 확인 필요")
    if anchor.get("status") not in ALLOWED_STATUSES:
        anchor["status"] = "REVIEW_NEEDED"
        issues.append(ValidationIssue("INVALID_STATUS", "허용되지 않은 상태입니다.", name))
    if anchor.get("parentRegionName") != city:
        anchor["parentRegionName"] = city
        issues.append(ValidationIssue("FIXED_PARENT_REGION", f"parentRegionName을 '{city}'로 교정했습니다.", name))
    if not CODE_PATTERN.fullmatch(str(anchor.get("code", ""))):
        issues.append(ValidationIssue("INVALID_CODE", "코드는 영문 대문자·숫자·밑줄 50자 이하여야 합니다.", name))
        _mark_review(anchor, "내부 코드 확인 필요")

    latitude = anchor.get("latitude")
    longitude = anchor.get("longitude")
    if (latitude is None) != (longitude is None):
        anchor["latitude"] = None
        anchor["longitude"] = None
        issues.append(ValidationIssue("PARTIAL_COORDINATE", "불완전한 좌표를 제거했습니다.", name))
    elif latitude is not None and not (33 <= latitude <= 39 and 124 <= longitude <= 132):
        anchor["latitude"] = None
        anchor["longitude"] = None
        issues.append(ValidationIssue("INVALID_KOREA_COORDINATE", "대한민국 범위를 벗어난 좌표를 제거했습니다.", name))
    if anchor.get("latitude") is None:
        _mark_review(anchor, "중심 좌표 검수 필요")

    radius = anchor.get("radiusMeters")
    if not isinstance(radius, int) or not 100 <= radius <= 5000:
        anchor["radiusMeters"] = None
        issues.append(ValidationIssue("INVALID_RADIUS", "반경은 100~5000m 정수여야 합니다.", name))
        _mark_review(anchor, "탐색 반경 검수 필요")

    source_urls = list(dict.fromkeys(anchor.get("sourceUrls", [])))
    unsupported_urls = sorted(set(source_urls) - allowed_urls)
    anchor["sourceUrls"] = [url for url in source_urls if url in allowed_urls]
    if unsupported_urls:
        issues.append(ValidationIssue("UNSUPPORTED_SOURCE", f"등록되지 않은 출처를 제거했습니다: {unsupported_urls}", name))
        _mark_review(anchor, "출처 검수 필요")
    if not anchor["sourceUrls"]:
        issues.append(ValidationIssue("MISSING_SOURCE", "근거 출처가 없습니다.", name))
        _mark_review(anchor, "근거 출처 필요")

    evidence = []
    for item in anchor.get("evidence", []):
        if item.get("sourceUrl") in allowed_urls and str(item.get("sourceQuote", "")).strip():
            evidence.append(
                {
                    "sourceUrl": item["sourceUrl"],
                    "sourceQuote": str(item["sourceQuote"]).strip()[:300],
                }
            )
    anchor["evidence"] = evidence
    if not evidence:
        _mark_review(anchor, "원문 근거 문구 검수 필요")

    anchor["aliases"] = list(
        dict.fromkeys(
            alias.strip()
            for alias in anchor.get("aliases", [])
            if isinstance(alias, str) and alias.strip() and normalize_name(alias) != normalize_name(name)
        )
    )
    anchor["description"] = str(anchor.get("description", "")).strip()[:500]
    return anchor, issues


def deduplicate_anchors(anchors: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[ValidationIssue]]:
    merged: dict[str, dict[str, Any]] = {}
    issues: list[ValidationIssue] = []
    for anchor in anchors:
        key = normalize_name(anchor.get("name", ""))
        if not key:
            continue
        if key not in merged:
            merged[key] = deepcopy(anchor)
            continue
        current = merged[key]
        current["sourceUrls"] = list(dict.fromkeys(current.get("sourceUrls", []) + anchor.get("sourceUrls", [])))
        current["aliases"] = list(dict.fromkeys(current.get("aliases", []) + anchor.get("aliases", [])))
        current["evidence"] = current.get("evidence", []) + anchor.get("evidence", [])
        current["reviewReasons"] = list(
            dict.fromkeys(current.get("reviewReasons", []) + anchor.get("reviewReasons", []))
        )
        if current.get("latitude") is None and anchor.get("latitude") is not None:
            current["latitude"] = anchor["latitude"]
            current["longitude"] = anchor["longitude"]
        if current.get("radiusMeters") is None and anchor.get("radiusMeters") is not None:
            current["radiusMeters"] = anchor["radiusMeters"]
        if current.get("status") == "REVIEW_NEEDED" or anchor.get("status") == "REVIEW_NEEDED":
            current["status"] = "REVIEW_NEEDED"
        issues.append(ValidationIssue("MERGED_DUPLICATE", f"중복 후보 '{anchor.get('name')}'를 병합했습니다.", anchor.get("name"), "INFO"))
    return list(merged.values()), issues


def validate_evaluation(
    evaluation: dict[str, Any], contract: dict[str, Any], anchor_code: str
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[ValidationIssue]]:
    activity_codes = {item["code"] for item in contract["activityCategories"]}
    preference_pairs = {
        (group["code"], option["code"])
        for group in contract["preferenceGroups"]
        for option in group["options"]
    }
    issues: list[ValidationIssue] = []
    activities: list[dict[str, Any]] = []
    seen_activities: set[str] = set()
    for rule in evaluation.get("activityRules", []):
        code = rule.get("activityCategoryCode")
        weight = rule.get("weight")
        if code not in activity_codes or weight not in ALLOWED_WEIGHTS or code in seen_activities:
            issues.append(ValidationIssue("INVALID_ACTIVITY_RULE", f"유효하지 않은 활동 규칙: {rule}", anchor_code))
            continue
        seen_activities.add(code)
        activities.append({"courseAnchorCode": anchor_code, **rule})

    preferences: list[dict[str, Any]] = []
    seen_preferences: set[tuple[str, str]] = set()
    for rule in evaluation.get("preferenceRules", []):
        pair = (rule.get("preferenceGroupCode"), rule.get("preferenceOptionCode"))
        weight = rule.get("weight")
        if pair not in preference_pairs or weight not in ALLOWED_WEIGHTS or pair in seen_preferences:
            issues.append(ValidationIssue("INVALID_PREFERENCE_RULE", f"유효하지 않은 선호도 규칙: {rule}", anchor_code))
            continue
        seen_preferences.add(pair)
        preferences.append({"courseAnchorCode": anchor_code, **rule})
    return activities, preferences, issues

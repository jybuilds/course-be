from __future__ import annotations

import sys
import unittest
from pathlib import Path


TOOL_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_DIR))

from anchor_pipeline.validator import (  # noqa: E402
    deduplicate_anchors,
    validate_anchor,
    validate_evaluation,
)


class AnchorValidatorTest(unittest.TestCase):
    def test_parent_region_is_city_and_existing_code_is_reused(self) -> None:
        source = "https://example.org/seongsu"
        anchor, issues = validate_anchor(
            {
                "code": "SEOUL_AREA_SEONGSU",
                "name": "성수동",
                "type": "AREA",
                "parentRegionName": "성동구",
                "aliases": ["성수"],
                "latitude": 37.5446,
                "longitude": 127.0557,
                "radiusMeters": 1500,
                "description": "테스트",
                "sourceUrls": [source],
                "evidence": [{"sourceUrl": source, "sourceQuote": "성수동"}],
                "status": "ACTIVE_CANDIDATE",
                "reviewReasons": [],
            },
            city="서울",
            allowed_urls={source},
            existing_anchors=[{"code": "SEONGSU", "name": "성수동", "aliases": ["성수"]}],
        )
        self.assertEqual("서울", anchor["parentRegionName"])
        self.assertEqual("SEONGSU", anchor["code"])
        self.assertTrue(any(issue.code == "FIXED_PARENT_REGION" for issue in issues))

    def test_unverified_source_and_missing_coordinate_require_review(self) -> None:
        anchor, issues = validate_anchor(
            {
                "code": "SEOUL_AREA_SAMPLE",
                "name": "테스트길",
                "type": "AREA",
                "parentRegionName": "서울",
                "aliases": [],
                "latitude": None,
                "longitude": None,
                "radiusMeters": 1000,
                "description": "테스트",
                "sourceUrls": ["https://hallucinated.example"],
                "evidence": [],
                "status": "ACTIVE_CANDIDATE",
                "reviewReasons": [],
            },
            city="서울",
            allowed_urls={"https://official.example"},
            existing_anchors=[],
        )
        self.assertEqual("REVIEW_NEEDED", anchor["status"])
        self.assertEqual([], anchor["sourceUrls"])
        self.assertTrue(any(issue.code == "UNSUPPORTED_SOURCE" for issue in issues))

    def test_duplicate_names_are_merged(self) -> None:
        first = {
            "name": "홍대·연남",
            "sourceUrls": ["a"],
            "aliases": ["홍대"],
            "evidence": [],
            "reviewReasons": [],
            "status": "ACTIVE_CANDIDATE",
            "latitude": None,
            "longitude": None,
            "radiusMeters": 1500,
        }
        second = {
            **first,
            "name": "홍대 연남",
            "sourceUrls": ["b"],
            "aliases": ["연남동"],
            "status": "REVIEW_NEEDED",
        }
        merged, issues = deduplicate_anchors([first, second])
        self.assertEqual(1, len(merged))
        self.assertEqual({"a", "b"}, set(merged[0]["sourceUrls"]))
        self.assertEqual("REVIEW_NEEDED", merged[0]["status"])
        self.assertEqual(1, len(issues))

    def test_evaluation_only_accepts_backend_codes_and_weight_scale(self) -> None:
        contract = {
            "activityCategories": [{"code": "CULTURE", "name": "전시·문화"}],
            "preferenceGroups": [
                {"code": "ENERGY", "options": [{"code": "QUIET", "name": "조용한"}]}
            ],
        }
        activities, preferences, issues = validate_evaluation(
            {
                "activityRules": [
                    {"activityCategoryCode": "CULTURE", "weight": 20, "reason": "근거", "confidence": 80},
                    {"activityCategoryCode": "UNKNOWN", "weight": 20, "reason": "오류", "confidence": 80},
                ],
                "preferenceRules": [
                    {"preferenceGroupCode": "ENERGY", "preferenceOptionCode": "QUIET", "weight": 15, "reason": "근거", "confidence": 80},
                    {"preferenceGroupCode": "ENERGY", "preferenceOptionCode": "QUIET", "weight": 13, "reason": "오류", "confidence": 80},
                ],
            },
            contract,
            "SEOCHON",
        )
        self.assertEqual(1, len(activities))
        self.assertEqual(1, len(preferences))
        self.assertEqual(2, len(issues))


if __name__ == "__main__":
    unittest.main()

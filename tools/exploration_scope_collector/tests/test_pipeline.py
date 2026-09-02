from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


TOOL_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_DIR))

from anchor_pipeline.io_utils import write_json  # noqa: E402
from anchor_pipeline.pipeline import CourseAnchorPipeline  # noqa: E402


class FakeLmStudioClient:
    model = "fake-qwen3-14b"

    def structured(self, *, prompt: str, schema_name: str, schema: dict) -> dict:
        if schema_name == "course_anchor_candidates":
            return {
                "anchors": [
                    {
                        "code": "SEOUL_AREA_TEST",
                        "name": "테스트거리",
                        "type": "AREA",
                        "parentRegionName": "서울",
                        "aliases": [],
                        "latitude": 37.57,
                        "longitude": 126.98,
                        "radiusMeters": 1000,
                        "description": "공식 원문에 근거한 테스트 권역",
                        "sourceUrls": ["https://official.example/area"],
                        "evidence": [
                            {
                                "sourceUrl": "https://official.example/area",
                                "sourceQuote": "테스트거리 관광 권역",
                            }
                        ],
                        "status": "ACTIVE_CANDIDATE",
                        "reviewReasons": [],
                    }
                ]
            }
        return {
            "activityRules": [
                {
                    "activityCategoryCode": "CULTURE",
                    "weight": 20,
                    "reason": "문화 활동 근거",
                    "confidence": 85,
                }
            ],
            "preferenceRules": [
                {
                    "preferenceGroupCode": "HIGHLIGHT",
                    "preferenceOptionCode": "SIGHTS_AND_PHOTOS",
                    "weight": 15,
                    "reason": "볼거리 근거",
                    "confidence": 80,
                }
            ],
            "reviewNotes": [],
        }


class PipelineIntegrationTest(unittest.TestCase):
    def test_normalize_evaluate_and_backend_export(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            pipeline = CourseAnchorPipeline(
                project_dir=TOOL_DIR,
                workspace_dir=workspace,
                lm_studio_url="http://unused",
                model=None,
            )
            pipeline.client = FakeLmStudioClient()
            raw_path = workspace / "seoul" / "raw" / "seoul_jongno_jung.json"
            write_json(
                raw_path,
                {
                    "sources": [
                        {
                            "url": "https://official.example/area",
                            "label": "공식 페이지",
                            "title": "테스트거리",
                            "text": "테스트거리 관광 권역",
                        }
                    ]
                },
            )

            normalized = pipeline.normalize("서울")
            self.assertEqual(1, len(normalized))
            pipeline.evaluate("서울")

            export_path = workspace / "seoul" / "catalogue" / "backend-import.json"
            exported = json.loads(export_path.read_text(encoding="utf-8"))
            self.assertEqual("서울", exported["courseAnchors"][0]["parentRegionName"])
            self.assertFalse(exported["courseAnchors"][0]["active"])
            self.assertEqual("CULTURE", exported["courseAnchorActivityRules"][0]["activityCategoryCode"])
            self.assertEqual("SIGHTS_AND_PHOTOS", exported["courseAnchorPreferenceRules"][0]["preferenceOptionCode"])


if __name__ == "__main__":
    unittest.main()

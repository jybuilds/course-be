from __future__ import annotations

from typing import Any


def _strict_object(properties: dict[str, Any], required: list[str]) -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": properties,
        "required": required,
    }


EVIDENCE_SCHEMA = _strict_object(
    {
        "sourceUrl": {"type": "string"},
        "sourceQuote": {"type": "string"},
    },
    ["sourceUrl", "sourceQuote"],
)

ANCHOR_SCHEMA = _strict_object(
    {
        "code": {"type": "string"},
        "name": {"type": "string"},
        "type": {"type": "string", "enum": ["AREA", "HUB"]},
        "parentRegionName": {"type": "string"},
        "aliases": {"type": "array", "items": {"type": "string"}},
        "latitude": {"type": ["number", "null"]},
        "longitude": {"type": ["number", "null"]},
        "radiusMeters": {"type": ["integer", "null"]},
        "description": {"type": "string"},
        "sourceUrls": {"type": "array", "items": {"type": "string"}},
        "evidence": {"type": "array", "items": EVIDENCE_SCHEMA},
        "status": {"type": "string", "enum": ["ACTIVE_CANDIDATE", "REVIEW_NEEDED"]},
        "reviewReasons": {"type": "array", "items": {"type": "string"}},
    },
    [
        "code",
        "name",
        "type",
        "parentRegionName",
        "aliases",
        "latitude",
        "longitude",
        "radiusMeters",
        "description",
        "sourceUrls",
        "evidence",
        "status",
        "reviewReasons",
    ],
)

ANCHOR_LIST_SCHEMA = _strict_object(
    {"anchors": {"type": "array", "items": ANCHOR_SCHEMA}},
    ["anchors"],
)

ACTIVITY_RULE_SCHEMA = _strict_object(
    {
        "activityCategoryCode": {"type": "string"},
        "weight": {"type": "integer"},
        "reason": {"type": "string"},
        "confidence": {"type": "integer"},
    },
    ["activityCategoryCode", "weight", "reason", "confidence"],
)

PREFERENCE_RULE_SCHEMA = _strict_object(
    {
        "preferenceGroupCode": {"type": "string"},
        "preferenceOptionCode": {"type": "string"},
        "weight": {"type": "integer"},
        "reason": {"type": "string"},
        "confidence": {"type": "integer"},
    },
    ["preferenceGroupCode", "preferenceOptionCode", "weight", "reason", "confidence"],
)

EVALUATION_SCHEMA = _strict_object(
    {
        "activityRules": {"type": "array", "items": ACTIVITY_RULE_SCHEMA},
        "preferenceRules": {"type": "array", "items": PREFERENCE_RULE_SCHEMA},
        "reviewNotes": {"type": "array", "items": {"type": "string"}},
    },
    ["activityRules", "preferenceRules", "reviewNotes"],
)

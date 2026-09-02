from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class SourceSpec:
    url: str
    source_type: str
    label: str

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "SourceSpec":
        return cls(
            url=str(value["url"]),
            source_type=str(value.get("sourceType", "OFFICIAL_WEB")),
            label=str(value.get("label", value["url"])),
        )


@dataclass
class SourceDocument:
    url: str
    source_type: str
    label: str
    title: str | None
    text: str
    fetched_at: str
    sha256: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class ValidationIssue:
    code: str
    message: str
    record_name: str | None = None
    severity: str = "WARNING"

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class BatchResult:
    collection_region_code: str
    collection_region_name: str
    status: str
    sources: list[SourceDocument] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "collectionRegionCode": self.collection_region_code,
            "collectionRegionName": self.collection_region_name,
            "status": self.status,
            "sources": [source.to_dict() for source in self.sources],
            "errors": self.errors,
        }

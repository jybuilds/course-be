from __future__ import annotations

import hashlib
import re
from datetime import UTC, datetime
from html.parser import HTMLParser
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .models import BatchResult, SourceDocument, SourceSpec


class HtmlTextExtractor(HTMLParser):
    SKIP_TAGS = {"script", "style", "noscript", "svg", "canvas"}

    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() in self.SKIP_TAGS:
            self._skip_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self.SKIP_TAGS and self._skip_depth:
            self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._skip_depth == 0:
            value = " ".join(data.split())
            if value:
                self._parts.append(value)

    def text(self) -> str:
        return "\n".join(self._parts)


def _fetch(spec: SourceSpec, timeout_seconds: int, max_chars: int) -> SourceDocument:
    request = Request(
        spec.url,
        headers={
            "User-Agent": "course-anchor-catalogue-research/1.0",
            "Accept": "text/html,application/xhtml+xml,application/json,text/plain;q=0.9,*/*;q=0.5",
        },
    )
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            raw = response.read()
            charset = response.headers.get_content_charset() or "utf-8"
            content_type = response.headers.get_content_type()
    except HTTPError as error:
        raise RuntimeError(f"HTTP {error.code}: {spec.url}") from error
    except URLError as error:
        raise RuntimeError(f"연결 실패: {spec.url} ({error.reason})") from error

    decoded = raw.decode(charset, errors="replace")
    if content_type in {"application/json", "text/plain"}:
        extracted = decoded
        title = spec.label
    else:
        parser = HtmlTextExtractor()
        parser.feed(decoded)
        extracted = parser.text()
        title_match = re.search(r"<title[^>]*>(.*?)</title>", decoded, re.I | re.S)
        title = (
            " ".join(re.sub(r"<[^>]+>", "", title_match.group(1)).split())
            if title_match
            else spec.label
        )

    extracted = extracted[:max_chars].strip()
    if not extracted:
        raise RuntimeError(f"본문을 추출하지 못했습니다: {spec.url}")
    return SourceDocument(
        url=spec.url,
        source_type=spec.source_type,
        label=spec.label,
        title=title,
        text=extracted,
        fetched_at=datetime.now(UTC).isoformat(),
        sha256=hashlib.sha256(raw).hexdigest(),
    )


def collect_batch(
    collection_region_code: str,
    collection_region_name: str,
    source_specs: Iterable[SourceSpec],
    *,
    timeout_seconds: int = 30,
    max_chars: int = 18_000,
) -> BatchResult:
    documents: list[SourceDocument] = []
    errors: list[str] = []
    seen: set[str] = set()
    for spec in source_specs:
        if spec.url in seen:
            continue
        seen.add(spec.url)
        try:
            documents.append(_fetch(spec, timeout_seconds, max_chars))
        except RuntimeError as error:
            errors.append(str(error))
    if documents and errors:
        status = "PARTIAL"
    elif documents:
        status = "COMPLETED"
    else:
        status = "FAILED"
    return BatchResult(collection_region_code, collection_region_name, status, documents, errors)

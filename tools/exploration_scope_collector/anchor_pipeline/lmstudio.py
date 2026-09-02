from __future__ import annotations

import json
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class LmStudioClient:
    def __init__(self, base_url: str, model: str | None, timeout_seconds: int = 600) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds
        self._resolved_model: str | None = None

    def _request(self, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            f"{self.base_url}{path}",
            data=body,
            headers={"Content-Type": "application/json"},
            method="GET" if payload is None else "POST",
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                return json.loads(response.read().decode("utf-8"))
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"LM Studio HTTP {error.code}: {detail[:500]}") from error
        except URLError as error:
            raise RuntimeError(
                f"LM Studio에 연결할 수 없습니다: {self.base_url}. Local Server 실행 상태를 확인하세요."
            ) from error

    def resolve_model(self) -> str:
        if self._resolved_model:
            return self._resolved_model
        response = self._request("/v1/models")
        models = [item.get("id") for item in response.get("data", []) if item.get("id")]
        if self.model:
            if self.model not in models:
                raise RuntimeError(
                    f"요청한 모델 '{self.model}'이 LM Studio에 없습니다. 현재 모델: {models or '없음'}"
                )
            self._resolved_model = self.model
            return self._resolved_model
        if len(models) == 1:
            self._resolved_model = models[0]
            return self._resolved_model
        if not models:
            raise RuntimeError("LM Studio에 로드되거나 사용 가능한 모델이 없습니다.")
        raise RuntimeError(f"모델이 여러 개입니다. --model로 지정하세요: {models}")

    def structured(self, *, prompt: str, schema_name: str, schema: dict[str, Any]) -> dict[str, Any]:
        model = self.resolve_model()
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": "당신은 출처 기반 데이터 정제기입니다. 반드시 주어진 JSON 스키마만 반환하세요.",
                },
                {"role": "user", "content": f"{prompt}\n\n/no_think"},
            ],
            "temperature": 0.1,
            "stream": False,
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": schema_name, "strict": True, "schema": schema},
            },
        }
        response = self._request("/v1/chat/completions", payload)
        try:
            content = response["choices"][0]["message"]["content"]
            return json.loads(content)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as error:
            raise RuntimeError("LM Studio가 유효한 구조화 JSON을 반환하지 않았습니다.") from error

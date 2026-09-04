import json
import threading
import time
from dataclasses import dataclass
from functools import lru_cache
from typing import Any, Protocol

import httpx

from .config import Settings
from .schemas import JobParseRequest, JobParseResult, LlmMatchOutput


class LlmProvider(Protocol):
    """Provider boundary; the rules parser never depends on a vendor SDK."""

    def parse_job(self, request: JobParseRequest, system_prompt: str) -> JobParseResult: ...

    def analyze_match(self, payload: dict[str, Any], system_prompt: str) -> tuple[LlmMatchOutput, dict[str, int]]: ...


@dataclass
class OpenAiCompatibleProvider:
    base_url: str
    api_key: str
    model: str
    timeout_seconds: float = 30.0
    max_retries: int = 2
    failure_threshold: int = 3
    reset_seconds: float = 60.0
    max_concurrency: int = 2

    def __post_init__(self):
        self._failure_count = 0
        self._opened_at: float | None = None
        self._lock = threading.Lock()
        self._semaphore = threading.BoundedSemaphore(self.max_concurrency)

    def parse_job(self, request: JobParseRequest, system_prompt: str) -> JobParseResult:
        body = self._chat(request.model_dump(), system_prompt)
        content = body["choices"][0]["message"]["content"]
        return JobParseResult.model_validate_json(content)

    def analyze_match(self, payload: dict[str, Any], system_prompt: str) -> tuple[LlmMatchOutput, dict[str, int]]:
        body = self._chat({"untrustedData": payload}, system_prompt)
        content = body["choices"][0]["message"]["content"]
        output = LlmMatchOutput.model_validate_json(content)
        usage = body.get("usage") or {}
        return output, {
            "prompt_tokens": int(usage.get("prompt_tokens", 0)),
            "completion_tokens": int(usage.get("completion_tokens", 0)),
        }

    def circuit_status(self) -> str:
        with self._lock:
            if self._opened_at is None:
                return "CLOSED"
            if time.monotonic() - self._opened_at < self.reset_seconds:
                return "OPEN"
            return "HALF_OPEN"

    def _chat(self, payload: dict[str, Any], system_prompt: str) -> dict[str, Any]:
        if not self._semaphore.acquire(timeout=self.timeout_seconds):
            raise RuntimeError("LLM concurrency limit reached")
        try:
            return self._chat_with_retries(payload, system_prompt)
        finally:
            self._semaphore.release()

    def _chat_with_retries(self, payload: dict[str, Any], system_prompt: str) -> dict[str, Any]:
        with self._lock:
            if self._opened_at is not None and time.monotonic() - self._opened_at < self.reset_seconds:
                raise RuntimeError("LLM circuit breaker is open")
            if self._opened_at is not None:
                self._opened_at = None
                self._failure_count = 0
        last_error: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                response = httpx.post(
                    f"{self.base_url.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
                    json={
                        "model": self.model,
                        "temperature": 0,
                        "response_format": {"type": "json_object"},
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
                        ],
                    },
                    timeout=self.timeout_seconds,
                )
                response.raise_for_status()
                with self._lock:
                    self._failure_count = 0
                return response.json()
            except (httpx.HTTPError, KeyError, ValueError) as exception:
                last_error = exception
                if attempt < self.max_retries:
                    time.sleep(min(0.25 * (2**attempt), 1.0))
        with self._lock:
            self._failure_count += 1
            if self._failure_count >= self.failure_threshold:
                self._opened_at = time.monotonic()
        raise RuntimeError("LLM provider request failed") from last_error


@lru_cache
def _cached_llm_provider(settings_key: tuple[str, str, str, float, int, int, float, int]) -> LlmProvider:
    base_url, api_key, model, timeout_seconds, max_retries, failure_threshold, reset_seconds, max_concurrency = settings_key
    return OpenAiCompatibleProvider(
        base_url,
        api_key,
        model,
        timeout_seconds,
        max_retries,
        failure_threshold,
        reset_seconds,
        max_concurrency,
    )


def create_llm_provider(settings: Settings) -> LlmProvider | None:
    if not (settings.llm_base_url and settings.llm_api_key and settings.llm_model):
        return None
    return _cached_llm_provider(
        (
            settings.llm_base_url,
            settings.llm_api_key,
            settings.llm_model,
            settings.llm_timeout_seconds,
            settings.llm_max_retries,
            settings.llm_circuit_failure_threshold,
            settings.llm_circuit_reset_seconds,
            settings.llm_max_concurrency,
        )
    )


def llm_provider_status(settings: Settings) -> str:
    provider = create_llm_provider(settings)
    if provider is None:
        return "NOT_CONFIGURED"
    if isinstance(provider, OpenAiCompatibleProvider):
        return provider.circuit_status()
    return "UNKNOWN"

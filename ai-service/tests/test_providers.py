import httpx
import pytest

from app.config import Settings
from app.providers import OpenAiCompatibleProvider, create_llm_provider


def test_provider_is_absent_without_explicit_configuration():
    assert create_llm_provider(Settings(llm_base_url=None, llm_api_key=None, llm_model=None)) is None


def test_openai_compatible_provider_is_selected_only_when_complete():
    provider = create_llm_provider(Settings(llm_base_url="https://llm.example/v1", llm_api_key="test-key", llm_model="test-model"))
    assert isinstance(provider, OpenAiCompatibleProvider)


def test_provider_factory_preserves_circuit_breaker_instance():
    settings = Settings(llm_base_url="https://llm.example/v1", llm_api_key="same-key", llm_model="same-model")
    provider = create_llm_provider(settings)
    assert provider is create_llm_provider(settings)
    assert provider.circuit_status() == "CLOSED"


def test_provider_retries_then_opens_circuit(monkeypatch):
    calls = 0

    def fail(*args, **kwargs):
        nonlocal calls
        calls += 1
        raise httpx.ConnectError("offline")

    monkeypatch.setattr(httpx, "post", fail)
    provider = OpenAiCompatibleProvider("https://llm.example/v1", "key", "model", max_retries=0, failure_threshold=2)
    with pytest.raises(RuntimeError, match="request failed"):
        provider.analyze_match({}, "system")
    with pytest.raises(RuntimeError, match="request failed"):
        provider.analyze_match({}, "system")
    with pytest.raises(RuntimeError, match="circuit breaker is open"):
        provider.analyze_match({}, "system")
    assert calls == 2


def test_provider_retries_timeouts_and_returns_a_safe_failure(monkeypatch):
    calls = 0

    def timeout(*args, **kwargs):
        nonlocal calls
        calls += 1
        raise httpx.ReadTimeout("deadline exceeded")

    monkeypatch.setattr(httpx, "post", timeout)
    monkeypatch.setattr("app.providers.time.sleep", lambda _: None)
    provider = OpenAiCompatibleProvider(
        "https://llm.example/v1", "key", "model", timeout_seconds=0.01, max_retries=2
    )
    with pytest.raises(RuntimeError, match="request failed"):
        provider.analyze_match({}, "system")
    assert calls == 3

import hashlib
import os

os.environ.setdefault("AI_SERVICE_INTERNAL_TOKEN", "test-only-internal-token")

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app

client = TestClient(app)


def payload():
    description = "要求熟悉 Java，Spring Boot 优先"
    return {
        "title": "Java 工程师",
        "companyName": "DEMO",
        "city": "杭州",
        "salaryText": "15-25K",
        "description": description,
        "contentHash": hashlib.sha256(description.encode()).hexdigest(),
    }


def test_health_and_readiness():
    assert client.get("/internal/v1/health").json() == {
        "status": "UP", "phase": "PHASE_11_OPERATIONAL_READINESS"
    }
    readiness = client.get("/internal/v1/readiness").json()
    assert readiness["status"] == "READY"
    assert readiness["circuitBreaker"] == "NOT_CONFIGURED"


def test_parse_requires_internal_token():
    assert client.post("/internal/v1/jobs/parse", json=payload()).status_code == 422
    assert client.post("/internal/v1/jobs/parse", json=payload(), headers={"X-Internal-Token": "wrong"}).status_code == 401


def test_parse_contract_and_trace():
    response = client.post("/internal/v1/jobs/parse", json=payload(), headers={"X-Internal-Token": get_settings().internal_token, "X-Trace-Id": "test-trace"})
    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] == "test-trace"
    assert response.json()["schemaVersion"] == "job-parser-v1"


def test_tailor_and_communication_internal_contracts():
    headers = {"X-Internal-Token": get_settings().internal_token, "X-Trace-Id": "phase6-test"}
    evidence = [{"evidenceRef": "SKILL:01:name:v1", "evidenceType": "SKILL", "text": "Java"}]
    tailor = client.post("/internal/v1/resumes/tailor", headers=headers, json={
        "schemaVersion": "resume-tailor-request-v1", "taskId": "t1", "traceId": "phase6-test",
        "userId": 1, "promptVersion": "resume_tailor/v1", "jobTitle": "Java Engineer",
        "companyName": "DEMO", "jobDescription": "Java", "strategy": "BALANCED",
        "baseResume": {"versionId": "01M1TEST000000000000000001", "content": {}, "renderedText": "Java", "sections": []},
        "evidence": evidence,
    })
    assert tailor.status_code == 200
    assert tailor.json()["truthCheckStatus"] == "VERIFIED"
    draft = client.post("/internal/v1/communications/draft", headers=headers, json={
        "schemaVersion": "communication-draft-request-v1", "taskId": "d1", "traceId": "phase6-test",
        "userId": 1, "promptVersion": "communication/v1", "channel": "BOSS", "purpose": "APPLICATION",
        "candidateName": "Lin", "jobTitle": "Java Engineer", "companyName": "DEMO", "evidence": evidence,
    })
    assert draft.status_code == 200
    assert draft.json()["externallySent"] is False
    assert 60 <= draft.json()["charCount"] <= 100

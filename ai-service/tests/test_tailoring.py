import pytest
from pydantic import ValidationError

from app.schemas import CommunicationDraftRequest, ResumeTailorRequest
from app.tailoring import draft_communication, tailor_resume


def evidence():
    return [
        {"evidenceRef": "SKILL:01:displayName:v1", "evidenceType": "SKILL", "text": "Java"},
        {"evidenceRef": "PROJECT:02:summary:v1", "evidenceType": "PROJECT", "text": "使用 Spring Boot 和 Redis 构建求职系统"},
    ]


def tailor_payload():
    return {
        "taskId": "tailor-test",
        "traceId": "trace-test",
        "userId": 1,
        "jobTitle": "Java Backend Engineer",
        "companyName": "DEMO",
        "jobDescription": "需要 Java、Spring Boot 和 Redis",
        "strategy": "ATS",
        "baseResume": {"versionId": "01M1TEST000000000000000001", "content": {}, "renderedText": "原始简历", "sections": []},
        "evidence": evidence(),
    }


def test_tailor_is_evidence_bound_and_deterministic():
    result = tailor_resume(ResumeTailorRequest.model_validate(tailor_payload()))
    assert result.executionMode == "RULES_ONLY"
    assert result.truthCheckStatus == "VERIFIED"
    assert all(change.evidenceRefs for change in result.changes)
    assert "Java" in result.proposedRenderedText
    repeated = tailor_resume(ResumeTailorRequest.model_validate(tailor_payload()))
    assert result.elapsedMs >= 0
    assert repeated.elapsedMs >= 0
    assert result.model_dump(exclude={"elapsedMs"}) == repeated.model_dump(exclude={"elapsedMs"})


def test_tailor_rejects_duplicate_evidence_refs():
    payload = tailor_payload()
    payload["evidence"] = [evidence()[0], evidence()[0]]
    with pytest.raises(ValueError, match="unique"):
        tailor_resume(ResumeTailorRequest.model_validate(payload))


@pytest.mark.parametrize("channel", ["BOSS", "LIEPIN", "EMAIL", "WECHAT", "THANK_YOU", "FOLLOW_UP", "OFFER"])
def test_all_communication_channels_create_drafts_only(channel):
    request = CommunicationDraftRequest(
        taskId=f"draft-{channel}", traceId="trace", userId=1, channel=channel,
        purpose="APPLICATION", candidateName="林同学", jobTitle="Java 工程师",
        companyName="示例公司", evidence=evidence(),
    )
    result = draft_communication(request)
    assert result.status == "DRAFT"
    assert result.externallySent is False
    assert result.evidenceRefs == [evidence()[0]["evidenceRef"]]
    if channel == "BOSS":
        assert 60 <= result.charCount <= 100


def test_strict_contract_rejects_unknown_fields():
    payload = tailor_payload()
    payload["ignorePreviousRules"] = True
    with pytest.raises(ValidationError):
        ResumeTailorRequest.model_validate(payload)

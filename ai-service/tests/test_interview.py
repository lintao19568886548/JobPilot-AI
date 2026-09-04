from pydantic import ValidationError

from app.interview import predict_interview, review_interview
from app.schemas import InterviewPredictionRequest, InterviewReviewRequest


def prediction_payload() -> dict:
    return {
        "schemaVersion": "interview-prediction-request-v1",
        "taskId": "task-1",
        "traceId": "trace-1",
        "userId": 1,
        "promptVersion": "interview_prediction/v1",
        "deadlineAt": "2026-09-02T12:00:00Z",
        "companyName": "Example",
        "role": "Backend Engineer",
        "jobDescription": "Java Redis systems",
        "companyContext": None,
        "resumeText": "Backend profile",
        "evidence": [{"evidenceRef": "evidence:1", "evidenceType": "SKILL", "text": "Java and Redis"}],
        "round": {"roundType": "TECHNICAL", "title": "Technical round", "format": "ONLINE"},
    }


def review_payload() -> dict:
    return {
        "schemaVersion": "interview-review-request-v1",
        "taskId": "task-2",
        "traceId": "trace-2",
        "userId": 1,
        "promptVersion": "interview_review/v1",
        "deadlineAt": "2026-09-02T12:00:00Z",
        "companyName": "Example",
        "role": "Backend Engineer",
        "actualAnswers": [
            {"questionRef": "question:1", "question": "Explain Redis", "answer": "I used Redis for caching", "selfRating": 3},
            {"questionRef": "question:2", "question": "Design a queue", "answer": "I described retries", "selfRating": 2},
        ],
        "candidateEvidence": [],
    }


def test_prediction_is_rules_only_and_predicted() -> None:
    response = predict_interview(InterviewPredictionRequest.model_validate(prediction_payload()))
    assert response.executionMode == "RULES_ONLY"
    assert response.promptVersion == "interview_prediction/v1"
    assert len(response.questions) == 6
    assert all(item.sourceType == "PREDICTED" for item in response.questions)


def test_prediction_cites_only_supplied_evidence() -> None:
    request = InterviewPredictionRequest.model_validate(prediction_payload())
    response = predict_interview(request)
    allowed = {item.evidenceRef for item in request.evidence}
    assert all(set(item.evidenceRefs) <= allowed for item in response.questions)


def test_prediction_without_evidence_discloses_gap() -> None:
    payload = prediction_payload()
    payload["evidence"] = []
    response = predict_interview(InterviewPredictionRequest.model_validate(payload))
    assert response.gaps
    assert all(not item.evidenceRefs for item in response.questions)


def test_prediction_schema_forbids_extra_fields() -> None:
    payload = prediction_payload()
    payload["cookie"] = "forbidden"
    try:
        InterviewPredictionRequest.model_validate(payload)
        raise AssertionError("extra field accepted")
    except ValidationError:
        pass


def test_review_separates_user_fact_from_inference() -> None:
    response = review_interview(InterviewReviewRequest.model_validate(review_payload()))
    assert response.status == "DRAFT"
    assert "规则模式" in response.summary
    assert any("Inference" in item.evidence for item in response.items)


def test_review_creates_proposed_gap_material_for_each_actual_answer() -> None:
    response = review_interview(InterviewReviewRequest.model_validate(review_payload()))
    gaps = [item for item in response.items if item.itemType == "KNOWLEDGE_GAP"]
    assert len(gaps) == 2
    assert {ref for item in gaps for ref in item.evidenceRefs} == {"question:1", "question:2"}


def test_review_rejects_unrecorded_empty_answer() -> None:
    payload = review_payload()
    payload["actualAnswers"][0]["answer"] = ""
    try:
        InterviewReviewRequest.model_validate(payload)
        raise AssertionError("blank answer accepted")
    except ValidationError:
        pass


def test_interview_output_schema_forbids_extra_fields() -> None:
    response = review_interview(InterviewReviewRequest.model_validate(review_payload())).model_dump()
    response["externalMessageSent"] = True
    from app.schemas import InterviewReviewResponse

    try:
        InterviewReviewResponse.model_validate(response)
        raise AssertionError("extra response field accepted")
    except ValidationError:
        pass

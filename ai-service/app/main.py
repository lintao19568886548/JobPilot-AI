import hmac
import uuid
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.responses import JSONResponse

from .config import Settings, get_settings
from .embeddings import service_for
from .interview import predict_interview, review_interview
from .matching import evaluate_match
from .parser import parse_job
from .providers import llm_provider_status
from .schemas import (
    CommunicationDraftRequest,
    CommunicationDraftResponse,
    EmbeddingRequest,
    EmbeddingResponse,
    InterviewPredictionRequest,
    InterviewPredictionResponse,
    InterviewReviewRequest,
    InterviewReviewResponse,
    JobParseRequest,
    JobParseResult,
    MatchEvaluateRequest,
    MatchEvaluateResponse,
    ResumeTailorRequest,
    ResumeTailorResponse,
)
from .tailoring import draft_communication, tailor_resume

app = FastAPI(title="JobPilot AI Service", version="0.11.0")


@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id") or uuid.uuid4().hex
    request.state.trace_id = trace_id
    try:
        response = await call_next(request)
    except Exception:
        response = JSONResponse(status_code=500, content={"code": "AI_INTERNAL_ERROR", "message": "AI service request failed", "traceId": trace_id})
    response.headers["X-Trace-Id"] = trace_id
    return response


def require_internal_token(
    x_internal_token: Annotated[str, Header(alias="X-Internal-Token")],
    settings: Annotated[Settings, Depends(get_settings)],
) -> Settings:
    if not settings.internal_token or not hmac.compare_digest(x_internal_token, settings.internal_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid internal credential")
    return settings


@app.get("/internal/v1/health")
def health() -> dict[str, str]:
    return {"status": "UP", "phase": "PHASE_11_OPERATIONAL_READINESS"}


@app.get("/internal/v1/readiness")
def readiness(settings: Annotated[Settings, Depends(get_settings)]) -> dict[str, str | bool | int]:
    mode = settings.parser_mode.upper()
    parser_configured = mode == "RULES_ONLY" or bool(settings.llm_api_key and settings.llm_model)
    configured = bool(settings.internal_token) and parser_configured
    return {
        "status": "READY" if configured else "NOT_READY",
        "parserMode": mode,
        "llmConfigured": bool(settings.llm_api_key and settings.llm_model),
        "circuitBreaker": llm_provider_status(settings),
        "embeddingProvider": settings.embedding_provider,
        "embeddingModel": settings.embedding_model,
        "embeddingDimension": settings.embedding_dimension,
        "milvusCollection": settings.milvus_collection,
    }


@app.post("/internal/v1/jobs/parse", response_model=JobParseResult)
def parse(request: JobParseRequest, settings: Annotated[Settings, Depends(require_internal_token)]) -> JobParseResult:
    try:
        return parse_job(request, settings)
    except RuntimeError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception


@app.post("/internal/v1/embeddings", response_model=EmbeddingResponse)
def embeddings(request: EmbeddingRequest, settings: Annotated[Settings, Depends(require_internal_token)]) -> EmbeddingResponse:
    try:
        response, _ = service_for(settings).embed(request)
        return response
    except (RuntimeError, ValueError) as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception


@app.post("/internal/v1/matches/evaluate", response_model=MatchEvaluateResponse)
def match_evaluate(request: MatchEvaluateRequest, settings: Annotated[Settings, Depends(require_internal_token)]) -> MatchEvaluateResponse:
    try:
        return evaluate_match(request, settings)
    except (RuntimeError, ValueError) as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception


@app.post("/internal/v1/resumes/tailor", response_model=ResumeTailorResponse)
def resume_tailor(request: ResumeTailorRequest, _: Annotated[Settings, Depends(require_internal_token)]) -> ResumeTailorResponse:
    try:
        return tailor_resume(request)
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)) from exception


@app.post("/internal/v1/communications/draft", response_model=CommunicationDraftResponse)
def communication_draft(
    request: CommunicationDraftRequest,
    _: Annotated[Settings, Depends(require_internal_token)],
) -> CommunicationDraftResponse:
    try:
        return draft_communication(request)
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)) from exception


@app.post("/internal/v1/interviews/predict", response_model=InterviewPredictionResponse)
def interview_predict(
    request: InterviewPredictionRequest,
    _: Annotated[Settings, Depends(require_internal_token)],
) -> InterviewPredictionResponse:
    try:
        return predict_interview(request)
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)) from exception


@app.post("/internal/v1/interviews/review", response_model=InterviewReviewResponse)
def interview_review(
    request: InterviewReviewRequest,
    _: Annotated[Settings, Depends(require_internal_token)],
) -> InterviewReviewResponse:
    try:
        return review_interview(request)
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)) from exception

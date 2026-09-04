import hashlib
import json
import time
from pathlib import Path
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from .config import Settings
from .embeddings import EmbeddingService, service_for
from .providers import create_llm_provider
from .schemas import EmbeddingRequest, LlmMatchOutput, MatchEvaluateRequest, MatchEvaluateResponse


class MatchingState(TypedDict, total=False):
    request: MatchEvaluateRequest
    settings: Settings
    embedding_service: EmbeddingService
    embedding_response: Any
    stored_embeddings: list[Any]
    embedding_score: float
    llm_status: str
    llm_output: LlmMatchOutput | None
    input_tokens: int | None
    output_tokens: int | None
    started: float
    result: MatchEvaluateResponse


def validate_input(state: MatchingState) -> MatchingState:
    request = state["request"]
    supplied = {item.evidenceRef for item in request.candidateEvidence}
    if len(supplied) != len(request.candidateEvidence):
        raise ValueError("candidateEvidence evidenceRef values must be unique")
    if len(request.documents) < 2 or request.documents[0].entityType != "JOB":
        raise ValueError("documents must begin with JOB followed by candidate evidence")
    return state


def embedding_similarity(state: MatchingState) -> MatchingState:
    request = state["request"]
    embedding_request = EmbeddingRequest(taskId=request.taskId, userId=request.userId, documents=request.documents)
    response, stored = state["embedding_service"].embed(embedding_request)
    job_vector = stored[0].vector
    candidate_vectors = [item.vector for item in stored[1:]]
    score = max(state["embedding_service"].cosine_score(job_vector, candidate) for candidate in candidate_vectors)
    return {**state, "embedding_response": response, "stored_embeddings": stored, "embedding_score": score}


def llm_analysis(state: MatchingState) -> MatchingState:
    settings, request = state["settings"], state["request"]
    provider = create_llm_provider(settings)
    if provider is None:
        return {**state, "llm_status": "SKIPPED_NOT_CONFIGURED", "llm_output": None, "input_tokens": None, "output_tokens": None}
    prompt_path = Path(__file__).resolve().parent.parent / "prompts" / "matching" / "v1.md"
    prompt = prompt_path.read_text(encoding="utf-8")
    payload = {
        "job": {
            "title": request.jobTitle,
            "company": request.companyName,
            "city": request.jobCity,
            "description": request.jobDescription,
            "skills": [skill.model_dump() for skill in request.jobSkills],
        },
        "candidateEvidence": [evidence.model_dump() for evidence in request.candidateEvidence],
        "candidateSkills": [skill.model_dump() for skill in request.candidateSkills],
        "allowedEvidenceRefs": [evidence.evidenceRef for evidence in request.candidateEvidence],
    }
    output, usage = provider.analyze_match(payload, prompt)
    return {
        **state,
        "llm_status": "SUCCEEDED",
        "llm_output": output,
        "input_tokens": usage.get("prompt_tokens"),
        "output_tokens": usage.get("completion_tokens"),
    }


def validate_output(state: MatchingState) -> MatchingState:
    output = state.get("llm_output")
    if output is None:
        return state
    allowed = {item.evidenceRef for item in state["request"].candidateEvidence}
    referenced = {
        evidence_ref
        for item in [*output.advantages, *output.gaps, *output.risks]
        for evidence_ref in item.candidateEvidenceRefs
    }
    unknown = sorted(referenced - allowed)
    if unknown:
        raise ValueError(f"LLM returned unknown evidence refs: {unknown}")
    if any(not item.candidateEvidenceRefs for item in output.advantages):
        raise ValueError("Every LLM advantage must cite at least one supplied candidate evidence ref")
    return state


def finalize(state: MatchingState) -> MatchingState:
    output = state.get("llm_output")
    response = MatchEvaluateResponse(
        embeddingScore=state["embedding_score"],
        embedding=state["embedding_response"],
        llmStatus=state["llm_status"],
        llmScore=output.llmScore if output else None,
        advantages=output.advantages if output else [],
        gaps=output.gaps if output else [],
        risks=output.risks if output else [],
        recommendation=output.recommendation if output else None,
        reason=output.reason if output else None,
        promptVersion=state["request"].promptVersion,
        modelName=state["settings"].llm_model if output else None,
        inputTokens=state.get("input_tokens"),
        outputTokens=state.get("output_tokens"),
        elapsedMs=max(0, int((time.perf_counter() - state["started"]) * 1000)),
    )
    return {**state, "result": response}


def build_matching_graph():
    graph = StateGraph(MatchingState)
    nodes = (
        ("validate_input", validate_input),
        ("embedding_similarity", embedding_similarity),
        ("llm_analysis", llm_analysis),
        ("validate_output", validate_output),
        ("finalize", finalize),
    )
    for name, function in nodes:
        graph.add_node(name, function)
    graph.add_edge(START, nodes[0][0])
    for current, following in zip(nodes, nodes[1:], strict=False):
        graph.add_edge(current[0], following[0])
    graph.add_edge(nodes[-1][0], END)
    return graph.compile()


MATCHING_GRAPH = build_matching_graph()


def evaluate_match(request: MatchEvaluateRequest, settings: Settings, embedding_service: EmbeddingService | None = None) -> MatchEvaluateResponse:
    state = MATCHING_GRAPH.invoke(
        {
            "request": request,
            "settings": settings,
            "embedding_service": embedding_service or service_for(settings),
            "started": time.perf_counter(),
        }
    )
    return MatchEvaluateResponse.model_validate(state["result"])


def request_hash(request: MatchEvaluateRequest) -> str:
    canonical = json.dumps(request.model_dump(), ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()

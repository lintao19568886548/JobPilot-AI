import re
import time
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .schemas import (
    CommunicationDraftRequest,
    CommunicationDraftResponse,
    ResumeTailorChange,
    ResumeTailorRequest,
    ResumeTailorResponse,
    TailorEvidence,
)


class TailorState(TypedDict, total=False):
    request: ResumeTailorRequest
    ranked: list[TailorEvidence]
    changes: list[ResumeTailorChange]
    started: float
    result: ResumeTailorResponse


def _tokens(value: str) -> set[str]:
    return {token.lower() for token in re.findall(r"[A-Za-z][A-Za-z0-9+#.\-]{1,30}|[\u4e00-\u9fff]{2,8}", value)}


def _validate_evidence(items: list[TailorEvidence]) -> None:
    refs = [item.evidenceRef for item in items]
    if len(refs) != len(set(refs)):
        raise ValueError("evidenceRef values must be unique")


def validate_tailor_input(state: TailorState) -> TailorState:
    _validate_evidence(state["request"].evidence)
    return state


def rank_evidence(state: TailorState) -> TailorState:
    request = state["request"]
    job_tokens = _tokens(f"{request.jobTitle} {request.jobDescription}")

    def score(item: TailorEvidence) -> tuple[int, int, str]:
        overlap = len(job_tokens & _tokens(item.text))
        type_weight = {"SKILL": 6, "PROJECT": 5, "EXPERIENCE": 4, "EDUCATION": 2}.get(item.evidenceType, 1)
        return (overlap * 20 + type_weight, type_weight, item.evidenceRef)

    return {**state, "ranked": sorted(request.evidence, key=score, reverse=True)}


def compose_changes(state: TailorState) -> TailorState:
    request, ranked = state["request"], state["ranked"]
    selected = ranked[: min(4, len(ranked))]
    summary_text = "；".join(item.text.strip() for item in selected)
    base_text = (request.baseResume.renderedText or "").strip()
    changes = [
        ResumeTailorChange(
            sectionType="SUMMARY",
            operation="REWRITE" if request.strategy != "ATS" else "ATS_KEYWORD",
            before=base_text[:2000] or None,
            after=f"候选人事实：{summary_text}",
            reason="优先展示与岗位描述重合且有候选人事实支持的内容",
            evidenceRefs=[item.evidenceRef for item in selected],
        )
    ]
    skills = [item for item in ranked if item.evidenceType == "SKILL"][:6]
    if skills:
        changes.append(
            ResumeTailorChange(
                sectionType="SKILLS",
                operation="REORDER",
                before=None,
                after="；".join(item.text.strip() for item in skills),
                reason="按岗位关键词相关性重排已存在技能，不新增技能",
                evidenceRefs=[item.evidenceRef for item in skills],
            )
        )
    return {**state, "changes": changes}


def validate_tailor_output(state: TailorState) -> TailorState:
    allowed = {item.evidenceRef: item.text for item in state["request"].evidence}
    for change in state["changes"]:
        if not change.evidenceRefs or any(ref not in allowed for ref in change.evidenceRefs):
            raise ValueError("Every Tailor change must cite supplied evidence")
        support = " ".join(allowed[ref] for ref in change.evidenceRefs)
        if not (_tokens(change.after) & _tokens(support)):
            raise ValueError("Tailor change is not grounded in cited evidence")
        supported_numbers = set(re.findall(r"\d+(?:\.\d+)?%?", f"{support} {change.before or ''}"))
        if set(re.findall(r"\d+(?:\.\d+)?%?", change.after)) - supported_numbers:
            raise ValueError("Tailor change contains an unsupported number")
    return state


def finalize_tailor(state: TailorState) -> TailorState:
    request, changes = state["request"], state["changes"]
    content = {
        "baseVersionId": request.baseResume.versionId,
        "strategy": request.strategy,
        "jobTitle": request.jobTitle,
        "changes": [change.model_dump() for change in changes],
    }
    rendered = "\n\n".join(change.after for change in changes)
    result = ResumeTailorResponse(
        promptVersion=request.promptVersion,
        changes=changes,
        proposedContent=content,
        proposedRenderedText=rendered,
        elapsedMs=max(0, int((time.perf_counter() - state["started"]) * 1000)),
    )
    return {**state, "result": result}


def build_tailor_graph():
    graph = StateGraph(TailorState)
    nodes = (
        ("validate_input", validate_tailor_input),
        ("rank_evidence", rank_evidence),
        ("compose_changes", compose_changes),
        ("validate_output", validate_tailor_output),
        ("finalize", finalize_tailor),
    )
    for name, function in nodes:
        graph.add_node(name, function)
    graph.add_edge(START, nodes[0][0])
    for current, following in zip(nodes, nodes[1:], strict=False):
        graph.add_edge(current[0], following[0])
    graph.add_edge(nodes[-1][0], END)
    return graph.compile()


TAILOR_GRAPH = build_tailor_graph()


def tailor_resume(request: ResumeTailorRequest) -> ResumeTailorResponse:
    state = TAILOR_GRAPH.invoke({"request": request, "started": time.perf_counter()})
    return ResumeTailorResponse.model_validate(state["result"])


def _short_fact(item: TailorEvidence) -> str:
    candidates = sorted(_tokens(item.text), key=lambda value: (-len(value), value))
    return candidates[0] if candidates else item.text.strip()[:16]


def _fit_boss(content: str) -> str:
    suffix = "如方便，期待进一步了解岗位团队与后续流程，感谢您的时间。"
    if len(content) < 60:
        content += suffix
    if len(content) > 100:
        content = content[:99].rstrip("，。； ") + "。"
    if len(content) < 60:
        content += "期待沟通。"
    return content


def draft_communication(request: CommunicationDraftRequest) -> CommunicationDraftResponse:
    started = time.perf_counter()
    _validate_evidence(request.evidence)
    evidence = request.evidence[0]
    fact = _short_fact(evidence)
    name = request.candidateName or "候选人"
    if request.channel == "BOSS":
        content = _fit_boss(
            f"您好，我是{name}，关注贵司{request.jobTitle}岗位。我具备{fact}相关实践，"
            "与岗位方向较匹配，希望有机会进一步沟通，谢谢。"
        )
    elif request.channel == "LIEPIN":
        content = f"您好，我关注到{request.companyName}的{request.jobTitle}岗位。我的候选人事实包括{fact}，希望进一步交流岗位要求与团队情况。"
    elif request.channel == "EMAIL":
        content = (
            f"您好：\n\n我希望申请{request.companyName}的{request.jobTitle}岗位。"
            f"我的相关事实为：{evidence.text}。随信附上针对该岗位整理的简历，期待进一步沟通。\n\n{name}"
        )
    elif request.channel == "WECHAT":
        content = f"您好，我是{name}。关于{request.jobTitle}岗位补充说明：{fact}。感谢您抽时间沟通。"
    elif request.channel == "THANK_YOU":
        content = f"感谢您就{request.companyName}{request.jobTitle}岗位与我沟通。结合交流内容，我的相关事实是{fact}。期待后续消息。"
    elif request.channel == "FOLLOW_UP":
        content = f"您好，想礼貌跟进{request.jobTitle}岗位的进展。我的相关候选人事实是{fact}，如需补充材料我可以及时提供。"
    else:
        content = f"您好，关于{request.companyName}{request.jobTitle}相关 Offer 沟通，我基于{fact}这一事实，希望进一步确认岗位与方案细节，谢谢。"
    return CommunicationDraftResponse(
        promptVersion=request.promptVersion,
        content=content,
        charCount=len(content),
        evidenceRefs=[evidence.evidenceRef],
        externallySent=False,
        elapsedMs=max(0, int((time.perf_counter() - started) * 1000)),
    )

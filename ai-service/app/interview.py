import time
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .schemas import (
    InterviewActualAnswer,
    InterviewEvidence,
    InterviewPredictedQuestion,
    InterviewPredictionRequest,
    InterviewPredictionResponse,
    InterviewReviewItem,
    InterviewReviewRequest,
    InterviewReviewResponse,
)


class PredictionState(TypedDict, total=False):
    request: InterviewPredictionRequest
    questions: list[InterviewPredictedQuestion]
    gaps: list[str]
    started: float
    result: InterviewPredictionResponse


def _support(evidence: list[InterviewEvidence], preferred: tuple[str, ...]) -> InterviewEvidence | None:
    return next((item for item in evidence if item.evidenceType in preferred), evidence[0] if evidence else None)


def _framework(item: InterviewEvidence | None) -> tuple[str, list[str]]:
    if item is None:
        return ("未提供可支撑回答的候选人事实。请如实说明边界，并描述你会如何学习或验证。", [])
    return (f"仅使用所引用的候选人事实作为证据，再说明背景、行动、权衡和结果：{item.text[:500]}", [item.evidenceRef])


def compose_prediction(state: PredictionState) -> PredictionState:
    request = state["request"]
    specs = [
        ("TECHNICAL_FOUNDATION", "MEDIUM", f"说明你胜任“{request.role}”岗位所依赖的核心技术概念。", ("SKILL", "EXPERIENCE")),
        (
            "PROJECT_DEEP_DIVE",
            "HARD",
            "选择一个相关项目，说明项目约束、你的决策以及可验证的结果。",
            ("PROJECT", "EXPERIENCE"),
        ),
        ("SYSTEM_DESIGN", "HARD", f"针对“{request.role}”岗位涉及的一个关键流程，设计一套可靠服务。", ("PROJECT", "SKILL")),
        ("BEHAVIORAL", "MEDIUM", "描述一次分歧或失败：你本人采取了什么行动，之后发生了什么变化？", ("EXPERIENCE", "PROJECT")),
        (
            "ROLE_RISK",
            "MEDIUM",
            "当前候选人证据对该岗位哪项要求的支撑最弱？你会如何补齐？",
            ("SKILL", "PROFILE"),
        ),
        (
            "FOLLOW_UP",
            "HARD",
            "如果流量、延迟或可靠性要求提高十倍，你会重新考虑哪些技术权衡？",
            ("PROJECT", "EXPERIENCE"),
        ),
    ]
    questions: list[InterviewPredictedQuestion] = []
    for category, difficulty, question, preferred in specs:
        item = _support(request.evidence, preferred)
        framework, refs = _framework(item)
        questions.append(
            InterviewPredictedQuestion(
                category=category,
                difficulty=difficulty,
                question=question,
                purpose=f"评估所选面试轮次中的 {category} 能力。",
            basis=(
                f"根据用户选择的 {request.round.roundType} 轮次以及提供的岗位/JD 上下文生成；"
                "这是预测题，不是实际面试事实。"
            ),
                answerFramework=framework,
                evidenceRefs=refs,
                suggestedFollowUps=["你考虑过哪些替代方案？", "你如何验证结果？"],
                riskNotes="不得虚构候选人证据中不存在的指标、职责、技术或结果。",
            )
        )
    gaps = [] if request.evidence else ["未提供候选人证据；回答框架无法引用个人事实。"]
    return {**state, "questions": questions, "gaps": gaps}


def validate_prediction(state: PredictionState) -> PredictionState:
    allowed = {item.evidenceRef for item in state["request"].evidence}
    for item in state["questions"]:
        if item.sourceType != "PREDICTED" or any(ref not in allowed for ref in item.evidenceRefs):
            raise ValueError("Prediction must remain PREDICTED and cite only supplied evidence")
    return state


def finalize_prediction(state: PredictionState) -> PredictionState:
    return {
        **state,
        "result": InterviewPredictionResponse(
            executionMode="RULES_ONLY",
            modelName=None,
            questions=state["questions"],
            gaps=state["gaps"],
            elapsedMs=max(0, int((time.perf_counter() - state["started"]) * 1000)),
        ),
    }


prediction_graph = StateGraph(PredictionState)
prediction_graph.add_node("compose", compose_prediction)
prediction_graph.add_node("validate", validate_prediction)
prediction_graph.add_node("finalize", finalize_prediction)
prediction_graph.add_edge(START, "compose")
prediction_graph.add_edge("compose", "validate")
prediction_graph.add_edge("validate", "finalize")
prediction_graph.add_edge("finalize", END)
prediction_workflow = prediction_graph.compile()


def predict_interview(request: InterviewPredictionRequest) -> InterviewPredictionResponse:
    state = prediction_workflow.invoke({"request": request, "started": time.perf_counter()})
    return state["result"]


class ReviewState(TypedDict, total=False):
    request: InterviewReviewRequest
    items: list[InterviewReviewItem]
    summary: str
    started: float
    result: InterviewReviewResponse


def _rating(answer: InterviewActualAnswer) -> int:
    return answer.selfRating if answer.selfRating is not None else 3


def compose_review(state: ReviewState) -> ReviewState:
    request = state["request"]
    answers = request.actualAnswers
    refs = [item.questionRef for item in answers]
    average = sum(_rating(item) for item in answers) / len(answers)
    first = answers[0]
    items = [
        InterviewReviewItem(
            itemType="STRENGTH",
            title="已记录真实问答",
            severity="LOW",
            description=f"用户记录了 {len(answers)} 个实际问题及回答，可据此进行复盘。",
            evidence=f"Fact: user supplied an answer for: {first.question[:300]}",
            evidenceRefs=[first.questionRef],
            recommendedActions=["保留问题原文与下一版回答，比较改进"],
        ),
        InterviewReviewItem(
            itemType="EXPRESSION_ISSUE",
            title="回答结构需要人工复核",
            severity="MEDIUM",
            description="规则模式不能判断语音表现；请人工检查回答是否明确区分背景、个人行动、权衡和结果。",
            evidence=f"Inference from the user-recorded answer structure only; self-rating={_rating(first)}.",
            evidenceRefs=[first.questionRef],
            recommendedActions=["用 STAR/问题-方案-权衡-结果四段式重写", "删除无法由事实支持的数字和结论"],
        ),
        InterviewReviewItem(
            itemType="NEXT_ACTION",
            title="进行一次证据约束的模拟回答",
            severity="LOW",
            description="下一次练习只引用 Candidate Evidence，并显式说明未知边界。",
            evidence=f"Based on {len(answers)} user-recorded actual answers; no audio or external meeting data was used.",
            evidenceRefs=refs,
            recommendedActions=["为每个实际问题写 90 秒版本", "为每项结论补充候选人事实引用"],
        ),
    ]
    for index, answer in enumerate(answers[:2], start=1):
        severity = "HIGH" if _rating(answer) <= 2 else "MEDIUM"
        items.append(
            InterviewReviewItem(
                itemType="KNOWLEDGE_GAP",
                title=f"实际问题 {index} 的知识与表达缺口",
                severity=severity,
                description="这是基于用户记录答案的待确认建议，不是已确认能力事实，也不会修改技能或简历。",
                evidence=f"Question: {answer.question[:500]} | User-recorded answer excerpt: {answer.answer[:800]} | self-rating={_rating(answer)}",
                evidenceRefs=[answer.questionRef],
                recommendedActions=["查缺补漏并整理一页知识卡", "用同一问题重新作答并由用户自行确认是否改善"],
            )
        )
    summary = f"复盘仅使用 {len(answers)} 条用户记录的实际问答；平均自评 {average:.1f}/5。规则模式输出均为待确认建议。"
    return {**state, "items": items, "summary": summary}


def validate_review(state: ReviewState) -> ReviewState:
    allowed = {item.questionRef for item in state["request"].actualAnswers}
    if any(any(ref not in allowed for ref in item.evidenceRefs) for item in state["items"]):
        raise ValueError("Review may cite only user-recorded actual answers")
    return state


def finalize_review(state: ReviewState) -> ReviewState:
    refs = [item.questionRef for item in state["request"].actualAnswers]
    return {
        **state,
        "result": InterviewReviewResponse(
            executionMode="RULES_ONLY",
            modelName=None,
            summary=state["summary"],
            evidenceRefs=refs,
            items=state["items"],
            elapsedMs=max(0, int((time.perf_counter() - state["started"]) * 1000)),
        ),
    }


review_graph = StateGraph(ReviewState)
review_graph.add_node("compose", compose_review)
review_graph.add_node("validate", validate_review)
review_graph.add_node("finalize", finalize_review)
review_graph.add_edge(START, "compose")
review_graph.add_edge("compose", "validate")
review_graph.add_edge("validate", "finalize")
review_graph.add_edge("finalize", END)
review_workflow = review_graph.compile()


def review_interview(request: InterviewReviewRequest) -> InterviewReviewResponse:
    state = review_workflow.invoke({"request": request, "started": time.perf_counter()})
    return state["result"]

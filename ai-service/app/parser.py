import re
import time
import unicodedata
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from .config import Settings
from .schemas import JobParseRequest, JobParseResult, SalaryResult, SkillResult


class ParserState(TypedDict, total=False):
    request: JobParseRequest
    text: str
    sentences: list[str]
    result: dict[str, Any]
    started: float


SKILLS: dict[str, tuple[str, tuple[str, ...]]] = {
    "java": ("Java", (r"\bjava\b",)),
    "spring_boot": ("Spring Boot", (r"spring\s*boot", r"springboot")),
    "spring_cloud_alibaba": ("Spring Cloud Alibaba", (r"spring\s*cloud\s*alibaba",)),
    "mysql": ("MySQL", (r"\bmysql(?:8)?\b",)),
    "redis": ("Redis", (r"\bredis(?:\s+cluster)?\b",)),
    "rocketmq": ("RocketMQ", (r"\brocketmq\b",)),
    "kafka": ("Kafka", (r"\bkafka\b",)),
    "kubernetes": ("Kubernetes", (r"\bkubernetes\b", r"\bk8s\b")),
    "docker": ("Docker", (r"\bdocker\b",)),
    "python": ("Python", (r"\bpython\b",)),
    "langchain": ("LangChain", (r"\blangchain\b",)),
    "langgraph": ("LangGraph", (r"\blanggraph\b",)),
    "rag": ("RAG", (r"\brag\b", r"检索增强生成")),
    "large_language_model": ("Large Language Model", (r"\bllm\b", r"大模型", r"large\s+language\s+model")),
    "vue_3": ("Vue 3", (r"\bvue\s*3\b", r"\bvue3\b")),
    "javascript": ("JavaScript", (r"\bjavascript\b", r"\bes6\b")),
}
NICE_MARKERS = ("优先", "加分", "更佳", "bonus", "preferred", "nice to have", "了解")
MUST_MARKERS = ("必须", "要求", "精通", "熟悉", "掌握", "required", "must", "proficient")


def normalize_text(value: str) -> str:
    return re.sub(r"[ \t]+", " ", unicodedata.normalize("NFKC", value).replace("\r", "\n")).strip()


def split_sentences(text: str) -> list[str]:
    return [part.strip(" -•\t") for part in re.split(r"[\n。；;，,]+|(?<!\d)\.(?!\d)", text) if part.strip(" -•\t")]


def sanitize_input(state: ParserState) -> ParserState:
    text = normalize_text(state["request"].description)
    warnings = ["PROMPT_INJECTION_TEXT_ISOLATED"] if re.search(r"忽略.{0,20}(指令|规则)|ignore.{0,20}(instruction|system)", text, re.I) else []
    return {**state, "text": text, "sentences": split_sentences(text), "result": {"warnings": warnings}}


def detect_language(state: ParserState) -> ParserState:
    chinese = len(re.findall(r"[\u4e00-\u9fff]", state["text"]))
    latin = len(re.findall(r"[A-Za-z]", state["text"]))
    state["result"]["language"] = "mixed" if chinese and latin > 20 else "zh" if chinese else "en" if latin else "unknown"
    return state


def extract_deterministic_fields(state: ParserState) -> ParserState:
    request, text = state["request"], state["text"]
    title = normalize_text(request.title) if request.title else None
    state["result"].update(
        normalizedTitle=title.lower() if title else None,
        companyName=request.companyName.strip() if request.companyName else None,
        city=request.city.strip() if request.city else None,
        district=None,
        workplaceText=None,
        remoteType="REMOTE" if re.search(r"远程|remote", text, re.I) else "HYBRID" if re.search(r"混合办公|hybrid", text, re.I) else "UNKNOWN",
        businessDomain=None,
        teamName=None,
    )
    return state


def normalize_salary(state: ParserState) -> ParserState:
    request = state["request"]
    source = request.salaryText or state["text"]
    salary = SalaryResult(originalText=request.salaryText, currency="CNY")
    monthly = re.search(r"(\d+(?:\.\d+)?)\s*[-~至]\s*(\d+(?:\.\d+)?)\s*[kK](?:\s*[·xX*]\s*(\d{1,2})\s*薪)?", source)
    annual = re.search(r"(\d+(?:\.\d+)?)\s*[-~至]\s*(\d+(?:\.\d+)?)\s*万\s*/?\s*年", source)
    if monthly:
        salary = SalaryResult(
            min=float(monthly.group(1)) * 1000,
            max=float(monthly.group(2)) * 1000,
            months=int(monthly.group(3) or 12),
            currency="CNY",
            originalText=monthly.group(0),
        )
    elif annual:
        salary = SalaryResult(
            min=float(annual.group(1)) * 10000 / 12, max=float(annual.group(2)) * 10000 / 12, months=12, currency="CNY", originalText=annual.group(0)
        )
    elif "面议" in source:
        salary = SalaryResult(min=None, max=None, months=None, currency="CNY", originalText="面议")
    state["result"]["salary"] = salary.model_dump()
    return state


def normalize_experience(state: ParserState) -> ParserState:
    text = state["text"]
    range_match = re.search(r"(\d+(?:\.\d+)?)\s*[-~至]\s*(\d+(?:\.\d+)?)\s*(?:年|years?)", text, re.I)
    minimum = re.search(r"(\d+(?:\.\d+)?)\s*(?:年(?:以上|及以上|经验)|years?(?:\s+experience|\s+required|\s+or more)?)", text, re.I)
    if "应届" in text or "校招" in text:
        state["result"].update(experienceMinYears=0.0, experienceMaxYears=0.0)
    elif range_match:
        state["result"].update(experienceMinYears=float(range_match.group(1)), experienceMaxYears=float(range_match.group(2)))
    elif minimum:
        state["result"].update(experienceMinYears=float(minimum.group(1)), experienceMaxYears=None)
    else:
        state["result"].update(experienceMinYears=None, experienceMaxYears=None)
    state["result"]["graduateYears"] = sorted({int(year) for year in re.findall(r"(20\d{2})\s*届", text)})
    return state


def normalize_education(state: ParserState) -> ParserState:
    state["result"]["education"] = next(
        (
            value
            for keyword, value in (
                (r"博士|doctor(?:ate)?|ph\.?d", "DOCTOR"),
                (r"硕士|master", "MASTER"),
                (r"本科|bachelor", "BACHELOR"),
                (r"大专|college|associate", "COLLEGE"),
                (r"高中|high school", "HIGH_SCHOOL"),
            )
            if re.search(keyword, state["text"], re.I)
        ),
        None,
    )
    return state


def classify_requirements(state: ParserState) -> ParserState:
    responsibilities, requirements, must, nice = [], [], [], []
    for sentence in state["sentences"]:
        lower = sentence.lower()
        if any(marker in lower for marker in ("负责", "职责", "参与", "设计", "开发", "responsib")):
            responsibilities.append(sentence)
        if any(marker in lower for marker in NICE_MARKERS):
            requirements.append(sentence)
            nice.append(sentence)
        elif any(marker in lower for marker in MUST_MARKERS):
            requirements.append(sentence)
            must.append(sentence)
    state["result"].update(
        responsibilities=responsibilities[:30], requirements=requirements[:50], mustHaveRequirements=must[:30], niceToHaveRequirements=nice[:30]
    )
    return state


def extract_skills(state: ParserState) -> ParserState:
    results, seen = [], set()
    for sentence in state["sentences"]:
        lower = sentence.lower()
        requirement_type = (
            "NICE_TO_HAVE" if any(marker in lower for marker in NICE_MARKERS) else "MUST_HAVE" if any(marker in lower for marker in MUST_MARKERS) else "RELATED"
        )
        years_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:年|years?)", sentence, re.I)
        for canonical, (display, patterns) in SKILLS.items():
            if any(re.search(pattern, sentence, re.I) for pattern in patterns) and (canonical, requirement_type) not in seen:
                seen.add((canonical, requirement_type))
                results.append(
                    SkillResult(
                        canonicalName=canonical,
                        displayName=display,
                        requirementType=requirement_type,
                        minYears=float(years_match.group(1)) if years_match else None,
                        evidenceText=sentence[:1000],
                        confidence=0.96 if requirement_type != "RELATED" else 0.82,
                    )
                )
    state["result"]["skills"] = [item.model_dump() for item in results]
    return state


def finalize(state: ParserState) -> ParserState:
    request, result = state["request"], state["result"]
    title_text = (request.title or "") + " " + state["text"][:500]
    result["jobType"] = (
        "INTERNSHIP"
        if re.search(r"实习|intern", title_text, re.I)
        else "PART_TIME"
        if re.search(r"兼职|part.?time", title_text, re.I)
        else "CONTRACT"
        if re.search(r"合同工|contract", title_text, re.I)
        else "FULL_TIME"
    )
    result.update(
        schemaVersion="job-parser-v1",
        parserVersion="rules-v1",
        parserMode="RULES_ONLY",
        contentHash=request.contentHash,
        promptVersion="job_parser/v1",
        modelName=None,
        elapsedMs=max(0, int((time.perf_counter() - state["started"]) * 1000)),
    )
    state["result"] = JobParseResult.model_validate(result).model_dump()
    return state


def build_graph():
    graph = StateGraph(ParserState)
    nodes = (
        ("sanitize_input", sanitize_input),
        ("detect_language", detect_language),
        ("extract_deterministic_fields", extract_deterministic_fields),
        ("normalize_salary", normalize_salary),
        ("normalize_experience", normalize_experience),
        ("normalize_education", normalize_education),
        ("classify_requirements", classify_requirements),
        ("extract_skills", extract_skills),
        ("finalize", finalize),
    )
    for name, function in nodes:
        graph.add_node(name, function)
    graph.add_edge(START, nodes[0][0])
    for current, following in zip(nodes, nodes[1:], strict=False):
        graph.add_edge(current[0], following[0])
    graph.add_edge(nodes[-1][0], END)
    return graph.compile()


PARSER_GRAPH = build_graph()


def parse_job(request: JobParseRequest, settings: Settings) -> JobParseResult:
    mode = settings.parser_mode.upper()
    if mode not in {"RULES_ONLY", "LLM_ONLY", "HYBRID"}:
        raise ValueError("JOB_PARSER_MODE must be RULES_ONLY, LLM_ONLY, or HYBRID")
    if mode != "RULES_ONLY" and (not settings.llm_api_key or not settings.llm_model):
        raise RuntimeError("LLM parser mode is not configured")
    if mode != "RULES_ONLY":
        raise RuntimeError("LLM provider is not activated without an explicit provider configuration")
    state = PARSER_GRAPH.invoke({"request": request, "started": time.perf_counter()})
    return JobParseResult.model_validate(state["result"])

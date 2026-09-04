from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class JobParseRequest(StrictModel):
    title: str | None = Field(default=None, max_length=200)
    companyName: str | None = Field(default=None, max_length=200)
    city: str | None = Field(default=None, max_length=100)
    salaryText: str | None = Field(default=None, max_length=120)
    description: str = Field(min_length=1, max_length=100_000)
    contentHash: str = Field(min_length=64, max_length=64)

    @field_validator("description")
    @classmethod
    def description_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("description must not be blank")
        return value


class SalaryResult(StrictModel):
    min: float | None = Field(default=None, ge=0)
    max: float | None = Field(default=None, ge=0)
    months: int | None = Field(default=None, ge=1, le=24)
    currency: str | None = Field(default="CNY", pattern=r"^[A-Z]{3}$")
    originalText: str | None = Field(default=None, max_length=120)


class SkillResult(StrictModel):
    canonicalName: str = Field(min_length=1, max_length=100)
    displayName: str = Field(min_length=1, max_length=100)
    requirementType: Literal["MUST_HAVE", "NICE_TO_HAVE", "RELATED"]
    minYears: float | None = Field(default=None, ge=0, le=80)
    evidenceText: str = Field(min_length=1, max_length=1000)
    confidence: float = Field(ge=0, le=1)


class JobParseResult(StrictModel):
    schemaVersion: Literal["job-parser-v1"] = "job-parser-v1"
    parserVersion: str = "rules-v1"
    parserMode: Literal["RULES_ONLY", "LLM_ONLY", "HYBRID"] = "RULES_ONLY"
    language: Literal["zh", "en", "mixed", "unknown"]
    normalizedTitle: str | None = None
    companyName: str | None = None
    city: str | None = None
    district: str | None = None
    workplaceText: str | None = None
    remoteType: Literal["ONSITE", "HYBRID", "REMOTE", "UNKNOWN"] = "UNKNOWN"
    salary: SalaryResult
    education: str | None = None
    experienceMinYears: float | None = Field(default=None, ge=0, le=80)
    experienceMaxYears: float | None = Field(default=None, ge=0, le=80)
    graduateYears: list[int] = Field(default_factory=list)
    jobType: Literal["FULL_TIME", "INTERNSHIP", "PART_TIME", "CONTRACT", "OTHER"]
    responsibilities: list[str] = Field(default_factory=list)
    requirements: list[str] = Field(default_factory=list)
    mustHaveRequirements: list[str] = Field(default_factory=list)
    niceToHaveRequirements: list[str] = Field(default_factory=list)
    skills: list[SkillResult] = Field(default_factory=list)
    businessDomain: str | None = None
    teamName: str | None = None
    warnings: list[str] = Field(default_factory=list)
    contentHash: str
    promptVersion: str | None = "job_parser/v1"
    modelName: str | None = None
    elapsedMs: int = Field(ge=0)


class EmbeddingDocument(StrictModel):
    entityType: Literal["JOB", "PROFILE", "RESUME_VERSION", "PROJECT", "SKILL_EVIDENCE"]
    entityId: int = Field(gt=0)
    entityPublicId: str = Field(min_length=1, max_length=26)
    text: str = Field(min_length=1, max_length=100_000)
    contentHash: str = Field(pattern=r"^[0-9a-f]{64}$")

    @field_validator("text")
    @classmethod
    def text_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("text must not be blank")
        return value


class EmbeddingRequest(StrictModel):
    schemaVersion: Literal["embedding-request-v1"] = "embedding-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    documents: list[EmbeddingDocument] = Field(min_length=1, max_length=50)


class EmbeddingRecord(StrictModel):
    entityType: str
    entityId: int
    entityPublicId: str
    vectorId: str
    contentHash: str
    collectionName: str
    provider: str
    model: str
    dimension: int = Field(gt=0)
    embeddingVersion: str
    cacheHit: bool


class EmbeddingResponse(StrictModel):
    schemaVersion: Literal["embedding-response-v1"] = "embedding-response-v1"
    records: list[EmbeddingRecord]
    provider: str
    model: str
    dimension: int = Field(gt=0)
    embeddingVersion: str
    elapsedMs: int = Field(ge=0)


class MatchSkill(StrictModel):
    canonicalName: str = Field(min_length=1, max_length=100)
    displayName: str = Field(min_length=1, max_length=100)
    requirementType: Literal["MUST_HAVE", "NICE_TO_HAVE", "RELATED"] | None = None
    evidenceText: str | None = Field(default=None, max_length=1000)


class CandidateEvidence(StrictModel):
    evidenceRef: str = Field(min_length=1, max_length=200)
    evidenceType: Literal["PROFILE", "SKILL", "PROJECT", "EXPERIENCE", "EDUCATION", "RESUME"]
    text: str = Field(min_length=1, max_length=5000)


class MatchEvaluateRequest(StrictModel):
    schemaVersion: Literal["match-evaluate-request-v1"] = "match-evaluate-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    traceId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    promptVersion: Literal["matching/v1"] = "matching/v1"
    jobTitle: str = Field(min_length=1, max_length=200)
    companyName: str = Field(min_length=1, max_length=200)
    jobCity: str | None = Field(default=None, max_length=100)
    jobDescription: str = Field(min_length=1, max_length=100_000)
    jobSkills: list[MatchSkill] = Field(default_factory=list, max_length=200)
    candidateSkills: list[MatchSkill] = Field(default_factory=list, max_length=200)
    candidateEvidence: list[CandidateEvidence] = Field(default_factory=list, max_length=300)
    documents: list[EmbeddingDocument] = Field(min_length=2, max_length=50)


class AnalysisItem(StrictModel):
    text: str = Field(min_length=1, max_length=500)
    candidateEvidenceRefs: list[str] = Field(default_factory=list, max_length=20)
    jobEvidence: str | None = Field(default=None, max_length=1000)
    severity: Literal["LOW", "MEDIUM", "HIGH"] | None = None


class LlmMatchOutput(StrictModel):
    llmScore: float = Field(ge=0, le=100)
    advantages: list[AnalysisItem] = Field(default_factory=list, max_length=20)
    gaps: list[AnalysisItem] = Field(default_factory=list, max_length=20)
    risks: list[AnalysisItem] = Field(default_factory=list, max_length=20)
    recommendation: Literal["RECOMMEND", "CONSIDER", "NOT_RECOMMENDED"]
    reason: str = Field(min_length=1, max_length=1000)


class MatchEvaluateResponse(StrictModel):
    schemaVersion: Literal["match-evaluate-response-v1"] = "match-evaluate-response-v1"
    embeddingScore: float = Field(ge=0, le=100)
    embedding: EmbeddingResponse
    llmStatus: Literal["SUCCEEDED", "FAILED", "SKIPPED_NOT_CONFIGURED"]
    llmScore: float | None = Field(default=None, ge=0, le=100)
    advantages: list[AnalysisItem] = Field(default_factory=list)
    gaps: list[AnalysisItem] = Field(default_factory=list)
    risks: list[AnalysisItem] = Field(default_factory=list)
    recommendation: Literal["RECOMMEND", "CONSIDER", "NOT_RECOMMENDED"] | None = None
    reason: str | None = Field(default=None, max_length=1000)
    promptVersion: str
    modelName: str | None = None
    inputTokens: int | None = Field(default=None, ge=0)
    outputTokens: int | None = Field(default=None, ge=0)
    elapsedMs: int = Field(ge=0)


class TailorEvidence(StrictModel):
    evidenceRef: str = Field(min_length=1, max_length=240)
    evidenceType: Literal["PROFILE", "EDUCATION", "EXPERIENCE", "PROJECT", "SKILL", "RESUME_SECTION"]
    text: str = Field(min_length=1, max_length=10_000)


class TailorSection(StrictModel):
    sectionType: Literal[
        "BASIC_INFO", "SUMMARY", "EDUCATION", "SKILLS", "EXPERIENCE", "PROJECTS",
        "CERTIFICATIONS", "AWARDS", "OPEN_SOURCE", "OTHER"
    ]
    content: dict | list
    sortOrder: int = Field(ge=0, le=10_000)


class TailorBaseResume(StrictModel):
    versionId: str = Field(min_length=1, max_length=26)
    content: dict
    renderedText: str | None = Field(default=None, max_length=100_000)
    sections: list[TailorSection] = Field(default_factory=list, max_length=30)


class ResumeTailorRequest(StrictModel):
    schemaVersion: Literal["resume-tailor-request-v1"] = "resume-tailor-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    traceId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    promptVersion: Literal["resume_tailor/v1"] = "resume_tailor/v1"
    jobTitle: str = Field(min_length=1, max_length=200)
    companyName: str = Field(min_length=1, max_length=200)
    jobDescription: str = Field(min_length=1, max_length=100_000)
    strategy: Literal["BALANCED", "ATS", "CONCISE"] = "BALANCED"
    baseResume: TailorBaseResume
    evidence: list[TailorEvidence] = Field(min_length=1, max_length=500)


class ResumeTailorChange(StrictModel):
    sectionType: Literal[
        "BASIC_INFO", "SUMMARY", "EDUCATION", "SKILLS", "EXPERIENCE", "PROJECTS",
        "CERTIFICATIONS", "AWARDS", "OPEN_SOURCE", "OTHER"
    ]
    operation: Literal["SELECT", "REORDER", "REWRITE", "COMPRESS", "ATS_KEYWORD"]
    before: str | None = Field(default=None, max_length=20_000)
    after: str = Field(min_length=1, max_length=20_000)
    reason: str = Field(min_length=1, max_length=1000)
    evidenceRefs: list[str] = Field(min_length=1, max_length=50)


class ResumeTailorResponse(StrictModel):
    schemaVersion: Literal["resume-tailor-response-v1"] = "resume-tailor-response-v1"
    status: Literal["READY"] = "READY"
    executionMode: Literal["RULES_ONLY", "LLM"] = "RULES_ONLY"
    promptVersion: str
    modelName: str | None = None
    truthCheckStatus: Literal["VERIFIED"] = "VERIFIED"
    changes: list[ResumeTailorChange] = Field(min_length=1, max_length=30)
    proposedContent: dict
    proposedRenderedText: str = Field(min_length=1, max_length=100_000)
    elapsedMs: int = Field(ge=0)


class CommunicationDraftRequest(StrictModel):
    schemaVersion: Literal["communication-draft-request-v1"] = "communication-draft-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    traceId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    promptVersion: Literal["communication/v1"] = "communication/v1"
    channel: Literal["BOSS", "LIEPIN", "EMAIL", "WECHAT", "THANK_YOU", "FOLLOW_UP", "OFFER"]
    purpose: str = Field(min_length=1, max_length=40)
    candidateName: str | None = Field(default=None, max_length=100)
    jobTitle: str = Field(min_length=1, max_length=200)
    companyName: str = Field(min_length=1, max_length=200)
    evidence: list[TailorEvidence] = Field(min_length=1, max_length=100)


class CommunicationDraftResponse(StrictModel):
    schemaVersion: Literal["communication-draft-response-v1"] = "communication-draft-response-v1"
    status: Literal["DRAFT"] = "DRAFT"
    executionMode: Literal["RULES_ONLY", "LLM"] = "RULES_ONLY"
    promptVersion: str
    modelName: str | None = None
    truthCheckStatus: Literal["VERIFIED"] = "VERIFIED"
    content: str = Field(min_length=1, max_length=5000)
    charCount: int = Field(gt=0, le=5000)
    evidenceRefs: list[str] = Field(min_length=1, max_length=20)
    externallySent: Literal[False] = False
    elapsedMs: int = Field(ge=0)


class InterviewEvidence(StrictModel):
    evidenceRef: str = Field(min_length=1, max_length=240)
    evidenceType: str = Field(min_length=1, max_length=80)
    text: str = Field(min_length=1, max_length=10_000)


class InterviewRoundContext(StrictModel):
    roundType: Literal["PHONE_SCREEN", "TECHNICAL", "SYSTEM_DESIGN", "BEHAVIORAL", "HR", "MANAGER", "FINAL", "OTHER"]
    title: str = Field(min_length=1, max_length=160)
    format: Literal["ONLINE", "ONSITE", "PHONE", "OTHER"]


class InterviewPredictionRequest(StrictModel):
    schemaVersion: Literal["interview-prediction-request-v1"] = "interview-prediction-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    traceId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    promptVersion: Literal["interview_prediction/v1"] = "interview_prediction/v1"
    deadlineAt: str
    companyName: str = Field(min_length=1, max_length=200)
    role: str = Field(min_length=1, max_length=200)
    jobDescription: str = Field(min_length=1, max_length=100_000)
    companyContext: str | None = Field(default=None, max_length=20_000)
    resumeText: str | None = Field(default=None, max_length=100_000)
    evidence: list[InterviewEvidence] = Field(default_factory=list, max_length=500)
    round: InterviewRoundContext


class InterviewPredictedQuestion(StrictModel):
    sourceType: Literal["PREDICTED"] = "PREDICTED"
    category: Literal["TECHNICAL_FOUNDATION", "PROJECT_DEEP_DIVE", "SYSTEM_DESIGN", "BEHAVIORAL", "ROLE_RISK", "FOLLOW_UP", "OTHER"]
    difficulty: Literal["EASY", "MEDIUM", "HARD"]
    question: str = Field(min_length=1, max_length=4000)
    purpose: str = Field(min_length=1, max_length=1000)
    basis: str = Field(min_length=1, max_length=2000)
    answerFramework: str = Field(min_length=1, max_length=20_000)
    evidenceRefs: list[str] = Field(default_factory=list, max_length=50)
    suggestedFollowUps: list[str] = Field(default_factory=list, max_length=20)
    riskNotes: str = Field(min_length=1, max_length=2000)


class InterviewPredictionResponse(StrictModel):
    schemaVersion: Literal["interview-prediction-response-v1"] = "interview-prediction-response-v1"
    status: Literal["READY"] = "READY"
    executionMode: Literal["RULES_ONLY", "LLM"] = "RULES_ONLY"
    promptVersion: Literal["interview_prediction/v1"] = "interview_prediction/v1"
    modelName: str | None = None
    questions: list[InterviewPredictedQuestion] = Field(min_length=1, max_length=30)
    gaps: list[str] = Field(default_factory=list, max_length=30)
    elapsedMs: int = Field(ge=0)


class InterviewActualAnswer(StrictModel):
    questionRef: str = Field(min_length=1, max_length=240)
    question: str = Field(min_length=1, max_length=4000)
    answer: str = Field(min_length=1, max_length=30_000)
    selfRating: int | None = Field(default=None, ge=1, le=5)


class InterviewReviewRequest(StrictModel):
    schemaVersion: Literal["interview-review-request-v1"] = "interview-review-request-v1"
    taskId: str = Field(min_length=1, max_length=64)
    traceId: str = Field(min_length=1, max_length=64)
    userId: int = Field(gt=0)
    promptVersion: Literal["interview_review/v1"] = "interview_review/v1"
    deadlineAt: str
    companyName: str = Field(min_length=1, max_length=200)
    role: str = Field(min_length=1, max_length=200)
    actualAnswers: list[InterviewActualAnswer] = Field(min_length=1, max_length=100)
    candidateEvidence: list[InterviewEvidence] = Field(default_factory=list, max_length=500)


class InterviewReviewItem(StrictModel):
    itemType: Literal["STRENGTH", "WEAKNESS", "EXPRESSION_ISSUE", "PROJECT_RISK", "NEXT_ACTION", "KNOWLEDGE_GAP"]
    title: str = Field(min_length=1, max_length=240)
    description: str = Field(min_length=1, max_length=4000)
    severity: Literal["LOW", "MEDIUM", "HIGH"]
    evidence: str = Field(min_length=1, max_length=4000)
    evidenceRefs: list[str] = Field(min_length=1, max_length=50)
    recommendedActions: list[str] = Field(default_factory=list, max_length=20)


class InterviewReviewResponse(StrictModel):
    schemaVersion: Literal["interview-review-response-v1"] = "interview-review-response-v1"
    status: Literal["DRAFT"] = "DRAFT"
    executionMode: Literal["RULES_ONLY", "LLM"] = "RULES_ONLY"
    promptVersion: Literal["interview_review/v1"] = "interview_review/v1"
    modelName: str | None = None
    summary: str = Field(min_length=1, max_length=20_000)
    evidenceRefs: list[str] = Field(min_length=1, max_length=100)
    items: list[InterviewReviewItem] = Field(min_length=1, max_length=100)
    elapsedMs: int = Field(ge=0)

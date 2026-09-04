package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.client.InterviewAiClient;
import com.jobpilot.interview.domain.*;
import com.jobpilot.interview.mapper.*;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.matching.domain.AiCallLogEntity;
import com.jobpilot.matching.service.AiCallLogService;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import com.jobpilot.tailoring.domain.CandidateEvidenceItemEntity;
import com.jobpilot.tailoring.mapper.CandidateEvidenceItemMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewAgentService {
    private static final String PREDICTION_PROMPT = "interview_prediction/v1";
    private static final String REVIEW_PROMPT = "interview_review/v1";
    private final InterviewMapper interviewMapper;
    private final InterviewRoundMapper rounds;
    private final InterviewQuestionMapper questions;
    private final InterviewAnswerNoteMapper notes;
    private final InterviewReviewMapper reviews;
    private final InterviewReviewItemMapper reviewItems;
    private final KnowledgeGapMapper gaps;
    private final KnowledgeGapEvidenceMapper gapEvidence;
    private final JobMapper jobs;
    private final CompanyMapper companies;
    private final ResumeVersionMapper versions;
    private final CandidateEvidenceItemMapper candidateEvidence;
    private final InterviewService interviews;
    private final InterviewViewAssembler views;
    private final InterviewAiClient ai;
    private final AiCallLogService aiLogs;
    private final AuditService audit;
    private final JsonCodec json;
    private final ObjectMapper objectMapper;

    public InterviewAgentService(InterviewMapper interviewMapper, InterviewRoundMapper rounds,
                                 InterviewQuestionMapper questions, InterviewAnswerNoteMapper notes,
                                 InterviewReviewMapper reviews, InterviewReviewItemMapper reviewItems,
                                 KnowledgeGapMapper gaps, KnowledgeGapEvidenceMapper gapEvidence,
                                 JobMapper jobs, CompanyMapper companies, ResumeVersionMapper versions,
                                 CandidateEvidenceItemMapper candidateEvidence, InterviewService interviews,
                                 InterviewViewAssembler views, InterviewAiClient ai, AiCallLogService aiLogs,
                                 AuditService audit, JsonCodec json, ObjectMapper objectMapper) {
        this.interviewMapper = interviewMapper; this.rounds = rounds; this.questions = questions; this.notes = notes;
        this.reviews = reviews; this.reviewItems = reviewItems; this.gaps = gaps; this.gapEvidence = gapEvidence;
        this.jobs = jobs; this.companies = companies; this.versions = versions; this.candidateEvidence = candidateEvidence;
        this.interviews = interviews; this.views = views; this.ai = ai; this.aiLogs = aiLogs; this.audit = audit;
        this.json = json; this.objectMapper = objectMapper;
    }

    @Transactional
    public List<QuestionView> predict(Long userId, String roundPublicId, String idempotencyKey) {
        validateKey(idempotencyKey);
        InterviewRoundEntity round = interviews.ownedRound(userId, roundPublicId);
        InterviewEntity interview = interviews.ownedInterviewById(userId, round.getInterviewId());
        Map<String, Object> payload = predictionPayload(userId, interview, round);
        String requestHash = hashPayload(payload);
        List<InterviewQuestionEntity> existing = predictedByKey(userId, round.getId(), idempotencyKey);
        if (!existing.isEmpty()) return samePrediction(existing, requestHash);

        AiCallLogEntity log = aiLogs.start(userId, "INTERVIEW_PREDICTION", null, PREDICTION_PROMPT,
                InterviewAiClient.sha256(PREDICTION_PROMPT), requestHash);
        AiResult<AiPredictionResponse> result;
        try {
            result = ai.predict(payload);
            validatePredictionEvidence(result.response(), suppliedEvidence(payload));
            aiLogs.finish(log.getId(), result.response().executionMode(), result.response().modelName(), result.responseHash(),
                    null, null, result.response().elapsedMs(), "SUCCEEDED", null);
        } catch (BusinessException exception) {
            aiLogs.finish(log.getId(), "INTERVIEW_AGENT", null, null, null, null,
                    null, "FAILED", Integer.toString(exception.getCode()));
            throw exception;
        }
        List<QuestionView> created = new ArrayList<>();
        int order = 0;
        try {
            for (AiQuestion item : result.response().questions()) {
                InterviewQuestionEntity entity = new InterviewQuestionEntity();
                entity.setUserId(userId); entity.setInterviewId(interview.getId()); entity.setRoundId(round.getId());
                entity.setSourceType("PREDICTED"); entity.setCategory(item.category()); entity.setDifficulty(item.difficulty());
                entity.setQuestionText(item.question()); entity.setPurposeText(item.purpose()); entity.setBasisText(item.basis());
                entity.setAnswerFramework(item.answerFramework()); entity.setSuggestedFollowUpsJson(json.write(item.suggestedFollowUps()));
                entity.setRiskNotes(item.riskNotes()); entity.setEvidenceRefsJson(json.write(item.evidenceRefs()));
                entity.setPredictionKey(idempotencyKey.trim()); entity.setRequestHash(requestHash);
                entity.setPromptVersion(result.response().promptVersion()); entity.setModelName(result.response().modelName());
                entity.setAiCallId(log.getId()); entity.setGeneratedAt(LocalDateTime.now(ZoneOffset.UTC)); entity.setDisplayOrder(order++);
                questions.insert(entity); created.add(views.question(entity));
            }
        } catch (DataIntegrityViolationException conflict) {
            List<InterviewQuestionEntity> concurrent = predictedByKey(userId, round.getId(), idempotencyKey);
            if (!concurrent.isEmpty()) return samePrediction(concurrent, requestHash);
            throw conflict;
        }
        audit.record(userId, "QUESTION_PREDICT", "INTERVIEW_ROUND", roundPublicId);
        return created;
    }

    @Transactional
    public ReviewView generateReview(Long userId, String interviewPublicId, String idempotencyKey) {
        validateKey(idempotencyKey);
        InterviewEntity interview = interviews.ownedInterview(userId, interviewPublicId);
        List<InterviewQuestionEntity> actualQuestions = questions.selectList(new LambdaQueryWrapper<InterviewQuestionEntity>()
                .eq(InterviewQuestionEntity::getUserId, userId).eq(InterviewQuestionEntity::getInterviewId, interview.getId())
                .eq(InterviewQuestionEntity::getSourceType, "ACTUAL").orderByAsc(InterviewQuestionEntity::getId));
        List<AiActualAnswer> actualAnswers = new ArrayList<>();
        for (InterviewQuestionEntity question : actualQuestions) {
            InterviewAnswerNoteEntity answer = notes.selectOne(new LambdaQueryWrapper<InterviewAnswerNoteEntity>()
                    .eq(InterviewAnswerNoteEntity::getUserId, userId).eq(InterviewAnswerNoteEntity::getQuestionId, question.getId())
                    .orderByDesc(InterviewAnswerNoteEntity::getNoteVersion).last("LIMIT 1"));
            if (answer != null) actualAnswers.add(new AiActualAnswer("question:" + question.getPublicId(),
                    question.getQuestionText(), answer.getAnswerText(), answer.getSelfRating()));
        }
        if (actualAnswers.isEmpty()) throw new ValidationException("At least one ACTUAL question with an Answer Note is required");
        Map<String, Object> payload = reviewPayload(userId, interview, actualAnswers);
        String requestHash = hashPayload(payload);
        InterviewReviewEntity existing = reviews.selectOne(new LambdaQueryWrapper<InterviewReviewEntity>()
                .eq(InterviewReviewEntity::getUserId, userId).eq(InterviewReviewEntity::getIdempotencyKey, idempotencyKey.trim()).last("LIMIT 1"));
        if (existing != null) return sameReview(existing, requestHash);

        AiCallLogEntity log = aiLogs.start(userId, "INTERVIEW_REVIEW", null, REVIEW_PROMPT,
                InterviewAiClient.sha256(REVIEW_PROMPT), requestHash);
        AiResult<AiReviewResponse> result;
        try {
            result = ai.review(payload);
            validateReviewEvidence(result.response(), actualAnswers);
            aiLogs.finish(log.getId(), result.response().executionMode(), result.response().modelName(), result.responseHash(),
                    null, null, result.response().elapsedMs(), "SUCCEEDED", null);
        } catch (BusinessException exception) {
            aiLogs.finish(log.getId(), "INTERVIEW_AGENT", null, null, null, null,
                    null, "FAILED", Integer.toString(exception.getCode()));
            throw exception;
        }

        InterviewReviewEntity review = new InterviewReviewEntity();
        review.setUserId(userId); review.setInterviewId(interview.getId()); review.setReviewVersion(nextReviewVersion(interview.getId()));
        review.setStatus("DRAFT"); review.setSummaryText(result.response().summary()); review.setSourceType(result.response().executionMode());
        review.setIdempotencyKey(idempotencyKey.trim()); review.setRequestHash(requestHash);
        review.setPromptVersion(result.response().promptVersion()); review.setModelName(result.response().modelName());
        review.setAiCallId(log.getId()); review.setEvidenceRefsJson(json.write(result.response().evidenceRefs())); review.setGeneratedAt(LocalDateTime.now(ZoneOffset.UTC));
        try { reviews.insert(review); }
        catch (DataIntegrityViolationException conflict) {
            InterviewReviewEntity concurrent = reviews.selectOne(new LambdaQueryWrapper<InterviewReviewEntity>()
                    .eq(InterviewReviewEntity::getUserId, userId).eq(InterviewReviewEntity::getIdempotencyKey, idempotencyKey.trim()).last("LIMIT 1"));
            if (concurrent != null) return sameReview(concurrent, requestHash);
            throw conflict;
        }
        int order = 0;
        for (AiReviewItem item : result.response().items()) {
            InterviewReviewItemEntity entity = new InterviewReviewItemEntity();
            entity.setUserId(userId); entity.setReviewId(review.getId()); entity.setItemType(item.itemType());
            entity.setTitle(item.title()); entity.setDescriptionText(item.description()); entity.setSeverity(item.severity());
            entity.setEvidenceText(item.evidence()); entity.setEvidenceRefsJson(json.write(item.evidenceRefs()));
            entity.setRecommendedActionsJson(json.write(item.recommendedActions())); entity.setDisplayOrder(order++); reviewItems.insert(entity);
            if ("KNOWLEDGE_GAP".equals(item.itemType())) createProposedGap(userId, interview, review, entity, item);
        }
        audit.record(userId, "REVIEW_GENERATE", "INTERVIEW_REVIEW", review.getPublicId());
        return views.review(review);
    }

    public List<ReviewView> listReviews(Long userId, String interviewPublicId) {
        InterviewEntity interview = interviews.ownedInterview(userId, interviewPublicId);
        return reviews.selectList(new LambdaQueryWrapper<InterviewReviewEntity>()
                .eq(InterviewReviewEntity::getUserId, userId).eq(InterviewReviewEntity::getInterviewId, interview.getId())
                .orderByDesc(InterviewReviewEntity::getReviewVersion)).stream().map(views::review).toList();
    }

    @Transactional
    public ReviewView confirmReview(Long userId, String publicId, VersionRequest request) {
        InterviewReviewEntity review = ownedReview(userId, publicId);
        InterviewService.version(review.getVersion(), request.version());
        if ("CONFIRMED".equals(review.getStatus())) return views.review(review);
        review.setStatus("CONFIRMED"); review.setConfirmedBy(userId); review.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC));
        reviews.updateById(review); audit.record(userId, "REVIEW_CONFIRM", "INTERVIEW_REVIEW", publicId);
        return views.review(ownedReview(userId, publicId));
    }

    private Map<String, Object> predictionPayload(Long userId, InterviewEntity interview, InterviewRoundEntity round) {
        JobEntity job = interview.getJobId() == null ? null : jobs.selectById(interview.getJobId());
        CompanyEntity company = interview.getCompanyId() == null ? null : companies.selectById(interview.getCompanyId());
        ResumeVersionEntity version = interview.getResumeVersionId() == null ? null : versions.selectById(interview.getResumeVersionId());
        List<AiEvidence> evidence = evidence(userId);
        Map<String, Object> payload = basePayload(userId, "interview-prediction-request-v1", PREDICTION_PROMPT);
        payload.put("companyName", interview.getCompanyNameSnapshot()); payload.put("role", interview.getRoleSnapshot());
        payload.put("jobDescription", job == null ? interview.getRoleSnapshot() : nonBlank(job.getDescriptionClean(), job.getDescriptionRaw()));
        payload.put("companyContext", company == null ? null : company.getDescription());
        payload.put("resumeText", version == null ? null : version.getRenderedText()); payload.put("evidence", evidence);
        payload.put("round", Map.of("roundType", round.getRoundType(), "title", round.getTitle(), "format", round.getFormat()));
        return payload;
    }

    private Map<String, Object> reviewPayload(Long userId, InterviewEntity interview, List<AiActualAnswer> actualAnswers) {
        Map<String, Object> payload = basePayload(userId, "interview-review-request-v1", REVIEW_PROMPT);
        payload.put("companyName", interview.getCompanyNameSnapshot()); payload.put("role", interview.getRoleSnapshot());
        payload.put("actualAnswers", actualAnswers); payload.put("candidateEvidence", evidence(userId));
        return payload;
    }

    private Map<String, Object> basePayload(Long userId, String schema, String prompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schema); payload.put("taskId", InterviewAiClient.sha256(TraceContext.getTraceId() + System.nanoTime()).substring(0, 32));
        payload.put("traceId", TraceContext.getTraceId()); payload.put("userId", userId); payload.put("promptVersion", prompt);
        payload.put("deadlineAt", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(20));
        return payload;
    }

    private List<AiEvidence> evidence(Long userId) {
        return candidateEvidence.selectList(new LambdaQueryWrapper<CandidateEvidenceItemEntity>()
                .eq(CandidateEvidenceItemEntity::getUserId, userId).orderByDesc(CandidateEvidenceItemEntity::getCreatedAt).last("LIMIT 100"))
                .stream().map(item -> new AiEvidence("evidence:" + item.getPublicId(), item.getEvidenceType(), item.getNormalizedText())).toList();
    }

    @SuppressWarnings("unchecked")
    private Set<String> suppliedEvidence(Map<String, Object> payload) {
        Set<String> refs = new LinkedHashSet<>();
        Object values = payload.get("evidence");
        if (values instanceof List<?> list) for (Object value : list) if (value instanceof AiEvidence item) refs.add(item.evidenceRef());
        return refs;
    }

    private void validatePredictionEvidence(AiPredictionResponse response, Set<String> supplied) {
        for (AiQuestion question : response.questions()) {
            if (!supplied.containsAll(question.evidenceRefs())) throw new BusinessException(5028006, "Prediction cited unknown evidence", HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateReviewEvidence(AiReviewResponse response, List<AiActualAnswer> actual) {
        Set<String> supplied = actual.stream().map(AiActualAnswer::questionRef).collect(java.util.stream.Collectors.toSet());
        if (!supplied.containsAll(response.evidenceRefs())) throw new BusinessException(5028007, "Review cited unknown answer evidence", HttpStatus.BAD_GATEWAY);
        for (AiReviewItem item : response.items()) if (!supplied.containsAll(item.evidenceRefs()))
            throw new BusinessException(5028007, "Review item cited unknown answer evidence", HttpStatus.BAD_GATEWAY);
    }

    private void createProposedGap(Long userId, InterviewEntity interview, InterviewReviewEntity review,
                                   InterviewReviewItemEntity reviewItem, AiReviewItem item) {
        KnowledgeGapEntity gap = new KnowledgeGapEntity();
        gap.setUserId(userId); gap.setSourceInterviewId(interview.getId()); gap.setSourceReviewId(review.getId());
        gap.setSourceReviewItemId(reviewItem.getId()); gap.setTitle(item.title()); gap.setCategory(category(item.title()));
        gap.setDescriptionText(item.description()); gap.setSeverity(item.severity());
        gap.setEvidenceJson(json.write(Map.of("type", "AI_INFERENCE", "text", item.evidence(), "refs", item.evidenceRefs())));
        gap.setRecommendedActionsJson(json.write(item.recommendedActions())); gap.setStatus("PROPOSED"); gaps.insert(gap);
        KnowledgeGapEvidenceEntity evidence = new KnowledgeGapEvidenceEntity();
        evidence.setUserId(userId); evidence.setKnowledgeGapId(gap.getId()); evidence.setInterviewId(interview.getId());
        evidence.setReviewId(review.getId()); evidence.setEvidenceType("AI_INFERENCE"); evidence.setEvidenceText(item.evidence()); gapEvidence.insert(evidence);
    }

    private List<InterviewQuestionEntity> predictedByKey(Long userId, Long roundId, String key) {
        return questions.selectList(new LambdaQueryWrapper<InterviewQuestionEntity>()
                .eq(InterviewQuestionEntity::getUserId, userId).eq(InterviewQuestionEntity::getRoundId, roundId)
                .eq(InterviewQuestionEntity::getPredictionKey, key.trim()).orderByAsc(InterviewQuestionEntity::getDisplayOrder));
    }

    private List<QuestionView> samePrediction(List<InterviewQuestionEntity> existing, String requestHash) {
        if (existing.stream().anyMatch(item -> !requestHash.equals(item.getRequestHash())))
            throw new BusinessException(4098005, "Idempotency-Key was used with different prediction input", HttpStatus.CONFLICT);
        return existing.stream().map(views::question).toList();
    }

    private ReviewView sameReview(InterviewReviewEntity existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) throw new BusinessException(4098006, "Idempotency-Key was used with different review input", HttpStatus.CONFLICT);
        return views.review(existing);
    }

    private int nextReviewVersion(Long interviewId) {
        InterviewReviewEntity latest = reviews.selectOne(new LambdaQueryWrapper<InterviewReviewEntity>()
                .eq(InterviewReviewEntity::getInterviewId, interviewId).orderByDesc(InterviewReviewEntity::getReviewVersion).last("LIMIT 1"));
        return latest == null ? 1 : latest.getReviewVersion() + 1;
    }

    private InterviewReviewEntity ownedReview(Long userId, String publicId) {
        InterviewReviewEntity value = reviews.selectOne(new LambdaQueryWrapper<InterviewReviewEntity>()
                .eq(InterviewReviewEntity::getUserId, userId).eq(InterviewReviewEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview Review");
        interviews.ownedInterviewById(userId, value.getInterviewId()); return value;
    }

    private String hashPayload(Map<String, Object> payload) {
        try {
            Map<String, Object> semanticPayload = new LinkedHashMap<>(payload);
            semanticPayload.remove("taskId");
            semanticPayload.remove("traceId");
            semanticPayload.remove("deadlineAt");
            return InterviewAiClient.sha256(objectMapper.writeValueAsString(semanticPayload));
        }
        catch (Exception exception) { throw new ValidationException("Interview AI payload is invalid"); }
    }
    private static String nonBlank(String preferred, String fallback) { return preferred == null || preferred.isBlank() ? fallback : preferred; }
    private static String category(String title) { return title.toLowerCase().contains("project") || title.contains("项目") ? "PROJECT" : "KNOWLEDGE"; }
    private static void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 120) throw new ValidationException("Valid Idempotency-Key is required");
    }
}

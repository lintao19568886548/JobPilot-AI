package com.jobpilot.tailoring.service;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.RecruiterEntity;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.matching.domain.AiCallLogEntity;
import com.jobpilot.matching.service.AiCallLogService;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.tailoring.client.TailoringAiClient;
import com.jobpilot.tailoring.domain.CommunicationDraftEntity;
import com.jobpilot.tailoring.domain.PromptTemplateEntity;
import com.jobpilot.tailoring.mapper.CommunicationDraftMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationDraftService {
    private final CommunicationDraftMapper mapper;
    private final TailoringReferenceService references;
    private final EvidenceLedgerService evidence;
    private final PromptTemplateService prompts;
    private final TailoringAiClient ai;
    private final TruthCheckService truth;
    private final AiCallLogService aiLogs;
    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final AuditService audit;

    public CommunicationDraftService(CommunicationDraftMapper mapper, TailoringReferenceService references,
                                     EvidenceLedgerService evidence, PromptTemplateService prompts,
                                     TailoringAiClient ai, TruthCheckService truth, AiCallLogService aiLogs,
                                     StringRedisTemplate redis, JsonCodec json, AuditService audit) {
        this.mapper = mapper; this.references = references; this.evidence = evidence; this.prompts = prompts;
        this.ai = ai; this.truth = truth; this.aiLogs = aiLogs; this.redis = redis; this.json = json; this.audit = audit;
    }

    public List<CommunicationDraftView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<CommunicationDraftEntity>()
                .eq(CommunicationDraftEntity::getUserId, userId).orderByDesc(CommunicationDraftEntity::getCreatedAt))
                .stream().map(item -> view(userId, item)).toList();
    }

    @Transactional
    public CommunicationDraftView create(Long userId, String jobPublicId, String idempotencyKey, DraftCreateRequest request) {
        validateKey(idempotencyKey);
        JobEntity job = references.job(userId, jobPublicId);
        CompanyEntity company = references.company(job.getCompanyId());
        ApplicationEntity application = references.application(userId, request.applicationId(), false);
        if (application != null && !application.getJobId().equals(job.getId())) throw new ValidationException("Application does not belong to this Job");
        RecruiterEntity recruiter = references.recruiter(userId, request.recruiterId(), false);
        ResumeVersionEntity resumeVersion;
        if (request.resumeVersionId() == null || request.resumeVersionId().isBlank()) {
            ResumeEntity master = references.master(userId);
            resumeVersion = references.versionById(userId, master.getCurrentVersionId());
        } else resumeVersion = references.version(userId, request.resumeVersionId());
        EvidenceLedgerView ledger = evidence.refresh(userId);
        PromptTemplateEntity prompt = prompts.active("COMMUNICATION_DRAFT");
        String requestHash = TailoringHash.sha256(jobPublicId + "|" + json.write(request));
        CommunicationDraftEntity existing = byIdempotency(userId, idempotencyKey);
        if (existing != null) return sameRequest(userId, existing, requestHash);
        String lockKey = "jobpilot:idempotency:draft:" + userId + ":" + TailoringHash.sha256(idempotencyKey);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, TraceContext.getTraceId(), Duration.ofSeconds(20)))) {
            throw new BusinessException(4096101, "Communication Draft is already being generated", HttpStatus.CONFLICT);
        }
        try {
            List<EvidenceView> orderedEvidence = ledger.items().stream().sorted(Comparator.comparingInt(this::priority)).toList();
            String name = ledger.items().stream().filter(item -> "fullName".equals(item.fieldPath()))
                    .map(EvidenceView::text).findFirst().orElse(null);
            String promptVersion = "communication/v" + prompt.getVersionNo();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", "communication-draft-request-v1"); payload.put("taskId", TailoringHash.sha256(idempotencyKey).substring(0, 32));
            payload.put("traceId", TraceContext.getTraceId()); payload.put("userId", userId); payload.put("promptVersion", promptVersion);
            payload.put("channel", request.channel()); payload.put("purpose", request.purpose()); payload.put("candidateName", name);
            payload.put("jobTitle", job.getTitle()); payload.put("companyName", company == null ? "Unknown Company" : company.getDisplayName());
            payload.put("evidence", orderedEvidence.stream().limit(100)
                    .map(item -> new AiEvidence(item.evidenceRef(), item.evidenceType(), item.text())).toList());
            String payloadHash = TailoringHash.sha256(json.write(payload));
            AiCallLogEntity aiLog = aiLogs.start(userId, "COMMUNICATION_DRAFT", prompt.getId(), promptVersion, prompt.getPromptHash(), payloadHash);
            AiResult<AiDraftResponse> result = ai.draft(payload);
            truth.draft(result.response(), ledger.items(), request.channel());
            aiLogs.finish(aiLog.getId(), "RULES_ENGINE", result.response().modelName(), result.responseHash(), null, null,
                    result.response().elapsedMs(), "SUCCEEDED", null);
            CommunicationDraftEntity draft = new CommunicationDraftEntity();
            draft.setUserId(userId); draft.setJobId(job.getId()); draft.setApplicationId(application == null ? null : application.getId());
            draft.setResumeVersionId(resumeVersion.getId()); draft.setRecruiterId(recruiter == null ? null : recruiter.getId());
            draft.setChannel(request.channel()); draft.setPurpose(request.purpose()); draft.setContent(result.response().content());
            draft.setCharCount(result.response().charCount()); draft.setEvidenceRefsJson(json.write(result.response().evidenceRefs()));
            draft.setPromptTemplateId(prompt.getId()); draft.setAiCallId(aiLog.getId()); draft.setExecutionMode(result.response().executionMode());
            draft.setTruthCheckStatus("VERIFIED"); draft.setStatus("DRAFT"); draft.setIdempotencyKey(idempotencyKey.trim());
            draft.setRequestHash(requestHash);
            try { mapper.insert(draft); }
            catch (DataIntegrityViolationException conflict) {
                CommunicationDraftEntity concurrent = byIdempotency(userId, idempotencyKey);
                if (concurrent != null) return sameRequest(userId, concurrent, requestHash);
                throw conflict;
            }
            audit.record(userId, "COMMUNICATION_DRAFT_CREATE", "COMMUNICATION_DRAFT", draft.getPublicId());
            return view(userId, draft);
        } finally { redis.delete(lockKey); }
    }

    public CommunicationDraftView get(Long userId, String publicId) { return view(userId, owned(userId, publicId)); }

    @Transactional
    public CommunicationDraftView approve(Long userId, String publicId, VersionRequest request) {
        CommunicationDraftEntity draft = owned(userId, publicId);
        requireVersion(draft, request.version());
        if ("APPROVED".equals(draft.getStatus()) || "USED".equals(draft.getStatus())) return view(userId, draft);
        if (!"DRAFT".equals(draft.getStatus()) || !"VERIFIED".equals(draft.getTruthCheckStatus())) {
            throw new BusinessException(4096102, "Only a VERIFIED Draft can be approved", HttpStatus.CONFLICT);
        }
        CommunicationDraftStateMachine.require(draft.getStatus(), "APPROVED");
        draft.setStatus("APPROVED"); draft.setApprovedAt(LocalDateTime.now()); mapper.updateById(draft);
        audit.record(userId, "COMMUNICATION_DRAFT_APPROVE", "COMMUNICATION_DRAFT", publicId);
        return view(userId, draft);
    }

    @Transactional
    public CommunicationDraftView markUsed(Long userId, String publicId, VersionRequest request) {
        CommunicationDraftEntity draft = owned(userId, publicId);
        requireVersion(draft, request.version());
        if ("USED".equals(draft.getStatus())) return view(userId, draft);
        if (!"APPROVED".equals(draft.getStatus())) {
            throw new BusinessException(4096103, "Draft must be approved before it can be marked used", HttpStatus.CONFLICT);
        }
        CommunicationDraftStateMachine.require(draft.getStatus(), "USED");
        draft.setStatus("USED"); draft.setUsedAt(LocalDateTime.now()); mapper.updateById(draft);
        audit.record(userId, "COMMUNICATION_DRAFT_MARK_USED", "COMMUNICATION_DRAFT", publicId);
        return view(userId, draft);
    }

    private CommunicationDraftView view(Long userId, CommunicationDraftEntity draft) {
        JobEntity job = references.job(userId, publicJobId(draft.getJobId()));
        CompanyEntity company = references.company(job.getCompanyId());
        ApplicationEntity application = draft.getApplicationId() == null ? null : references.applicationById(userId, draft.getApplicationId());
        ResumeVersionEntity resume = references.versionById(userId, draft.getResumeVersionId());
        RecruiterEntity recruiter = draft.getRecruiterId() == null ? null : references.recruiterById(userId, draft.getRecruiterId());
        return new CommunicationDraftView(draft.getPublicId(), job.getPublicId(), job.getTitle(),
                company == null ? null : company.getDisplayName(), application == null ? null : application.getPublicId(),
                resume.getPublicId(), recruiter == null ? null : recruiter.getPublicId(), draft.getChannel(), draft.getPurpose(),
                draft.getContent(), draft.getCharCount(), json.readStringList(draft.getEvidenceRefsJson()), promptVersion(draft.getPromptTemplateId()),
                draft.getExecutionMode(), draft.getTruthCheckStatus(), draft.getStatus(), false,
                draft.getApprovedAt(), draft.getUsedAt(), draft.getVersion(), draft.getCreatedAt(), draft.getUpdatedAt());
    }

    private int priority(EvidenceView item) {
        return switch (item.evidenceType()) { case "SKILL" -> 0; case "PROJECT" -> 1; case "EXPERIENCE" -> 2; default -> 3; };
    }
    private String promptVersion(Long id) {
        PromptTemplateEntity prompt = references.promptById(id);
        return prompt == null ? null : prompt.getTemplateKey().toLowerCase() + "/v" + prompt.getVersionNo();
    }
    private String publicJobId(Long id) {
        JobEntity job = references.jobByDatabaseId(id); if (job == null) throw new ResourceNotFoundException("Job"); return job.getPublicId();
    }
    private CommunicationDraftEntity owned(Long userId, String publicId) {
        CommunicationDraftEntity entity = mapper.selectOne(new LambdaQueryWrapper<CommunicationDraftEntity>()
                .eq(CommunicationDraftEntity::getUserId, userId).eq(CommunicationDraftEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Communication Draft"); return entity;
    }
    private CommunicationDraftEntity byIdempotency(Long userId, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<CommunicationDraftEntity>()
                .eq(CommunicationDraftEntity::getUserId, userId).eq(CommunicationDraftEntity::getIdempotencyKey, key.trim()).last("LIMIT 1"));
    }
    private CommunicationDraftView sameRequest(Long userId, CommunicationDraftEntity entity, String hash) {
        if (!hash.equals(entity.getRequestHash())) throw new BusinessException(4096104, "Idempotency-Key was used with different Draft input", HttpStatus.CONFLICT);
        return view(userId, entity);
    }
    private static void requireVersion(CommunicationDraftEntity entity, Integer version) {
        if (version == null || !entity.getVersion().equals(version)) throw new BusinessException(4096105, "Communication Draft version conflict", HttpStatus.CONFLICT);
    }
    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) throw new ValidationException("Valid Idempotency-Key is required");
    }
}

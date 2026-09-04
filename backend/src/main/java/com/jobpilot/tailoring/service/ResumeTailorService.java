package com.jobpilot.tailoring.service;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.jobpilot.resume.domain.ResumeSectionEntity;
import com.jobpilot.resume.domain.ResumeSectionType;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.dto.ResumeDtos.ResumeCreateRequest;
import com.jobpilot.resume.dto.ResumeDtos.ResumeVersionView;
import com.jobpilot.resume.dto.ResumeDtos.SectionRequest;
import com.jobpilot.resume.dto.ResumeDtos.TailoredVersionCommand;
import com.jobpilot.resume.mapper.ResumeSectionMapper;
import com.jobpilot.resume.service.ResumeService;
import com.jobpilot.tailoring.client.TailoringAiClient;
import com.jobpilot.tailoring.domain.PromptTemplateEntity;
import com.jobpilot.tailoring.domain.ResumeTailorChangeEntity;
import com.jobpilot.tailoring.domain.ResumeTailorRunEntity;
import com.jobpilot.tailoring.mapper.ResumeTailorChangeMapper;
import com.jobpilot.tailoring.mapper.ResumeTailorRunMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeTailorService {
    private final ResumeTailorRunMapper runs;
    private final ResumeTailorChangeMapper changes;
    private final ResumeSectionMapper sections;
    private final TailoringReferenceService references;
    private final EvidenceLedgerService evidence;
    private final PromptTemplateService prompts;
    private final TailoringAiClient ai;
    private final TruthCheckService truth;
    private final AiCallLogService aiLogs;
    private final ResumeService resumes;
    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final AuditService audit;

    public ResumeTailorService(ResumeTailorRunMapper runs, ResumeTailorChangeMapper changes,
                               ResumeSectionMapper sections, TailoringReferenceService references,
                               EvidenceLedgerService evidence, PromptTemplateService prompts,
                               TailoringAiClient ai, TruthCheckService truth, AiCallLogService aiLogs,
                               ResumeService resumes, StringRedisTemplate redis, JsonCodec json, AuditService audit) {
        this.runs = runs; this.changes = changes; this.sections = sections; this.references = references;
        this.evidence = evidence; this.prompts = prompts; this.ai = ai; this.truth = truth; this.aiLogs = aiLogs;
        this.resumes = resumes; this.redis = redis; this.json = json; this.audit = audit;
    }

    public List<TailorRunView> list(Long userId) {
        return runs.selectList(new LambdaQueryWrapper<ResumeTailorRunEntity>()
                .eq(ResumeTailorRunEntity::getUserId, userId).orderByDesc(ResumeTailorRunEntity::getCreatedAt))
                .stream().map(run -> view(userId, run)).toList();
    }

    @Transactional
    public TailorRunView create(Long userId, String jobPublicId, String idempotencyKey, TailorCreateRequest request) {
        validateKey(idempotencyKey);
        String strategy = request.strategy() == null ? "BALANCED" : request.strategy();
        JobEntity job = references.job(userId, jobPublicId);
        CompanyEntity company = references.company(job.getCompanyId());
        ResumeEntity master = references.master(userId);
        ResumeVersionEntity base = request.baseResumeVersionId() == null || request.baseResumeVersionId().isBlank()
                ? references.versionById(userId, master.getCurrentVersionId())
                : references.version(userId, request.baseResumeVersionId());
        if (!base.getResumeId().equals(master.getId()) || !base.getId().equals(master.getCurrentVersionId())) {
            throw new ValidationException("Tailor must use the active Master Resume version");
        }
        ResumeEntity target = request.targetResumeId() == null || request.targetResumeId().isBlank()
                ? null : references.resume(userId, request.targetResumeId());
        if (target != null && Boolean.TRUE.equals(target.getMaster())) {
            throw new ValidationException("Target Resume must not be the Master Resume");
        }
        EvidenceLedgerView ledger = evidence.refresh(userId);
        PromptTemplateEntity prompt = prompts.active("RESUME_TAILOR");
        String requestHash = TailoringHash.sha256(jobPublicId + "|" + json.write(request));
        ResumeTailorRunEntity existing = byIdempotency(userId, idempotencyKey);
        if (existing != null) return sameRequest(userId, existing, requestHash);
        String lockKey = "jobpilot:idempotency:tailor:" + userId + ":" + TailoringHash.sha256(idempotencyKey);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, TraceContext.getTraceId(), Duration.ofSeconds(30)))) {
            throw new BusinessException(4096002, "Tailor request is already in progress", HttpStatus.CONFLICT);
        }
        try {
            List<AiSection> baseSections = sections.selectList(new LambdaQueryWrapper<ResumeSectionEntity>()
                    .eq(ResumeSectionEntity::getResumeVersionId, base.getId()).orderByAsc(ResumeSectionEntity::getSortOrder))
                    .stream().map(item -> new AiSection(item.getSectionType(), json.readNode(item.getContentJson()), item.getSortOrder())).toList();
            String promptVersion = "resume_tailor/v" + prompt.getVersionNo();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", "resume-tailor-request-v1"); payload.put("taskId", TailoringHash.sha256(idempotencyKey).substring(0, 32));
            payload.put("traceId", TraceContext.getTraceId()); payload.put("userId", userId); payload.put("promptVersion", promptVersion);
            payload.put("jobTitle", job.getTitle()); payload.put("companyName", company == null ? "Unknown Company" : company.getDisplayName());
            String description = job.getDescriptionClean() == null ? job.getDescriptionRaw() : job.getDescriptionClean();
            payload.put("jobDescription", description == null || description.isBlank() ? job.getTitle() : description);
            payload.put("strategy", strategy); payload.put("baseResume", new AiBaseResume(base.getPublicId(),
                    json.readNode(base.getContentJson()), base.getRenderedText(), baseSections));
            payload.put("evidence", ledger.items().stream().map(item -> new AiEvidence(item.evidenceRef(), item.evidenceType(), item.text())).toList());
            String payloadHash = TailoringHash.sha256(json.write(payload));
            AiCallLogEntity aiLog = aiLogs.start(userId, "RESUME_TAILOR", prompt.getId(), promptVersion, prompt.getPromptHash(), payloadHash);
            AiResult<AiTailorResponse> result = ai.tailor(payload);
            truth.tailor(result.response().changes(), ledger.items());
            aiLogs.finish(aiLog.getId(), "RULES_ENGINE", result.response().modelName(), result.responseHash(), null, null,
                    result.response().elapsedMs(), "SUCCEEDED", null);
            ResumeTailorRunEntity run = new ResumeTailorRunEntity();
            run.setUserId(userId); run.setJobId(job.getId()); run.setBaseResumeVersionId(base.getId());
            run.setTargetResumeId(target == null ? null : target.getId()); run.setPromptTemplateId(prompt.getId()); run.setAiCallId(aiLog.getId());
            run.setIdempotencyKey(idempotencyKey.trim()); run.setRequestHash(requestHash);
            run.setInputHash(TailoringHash.sha256(payloadHash + "|" + ledger.snapshotHash())); run.setStrategy(strategy); run.setStatus("READY");
            run.setExecutionMode(result.response().executionMode()); run.setPromptVersion(promptVersion); run.setModelName(result.response().modelName());
            run.setTruthCheckStatus("VERIFIED"); run.setEvidenceSnapshotHash(ledger.snapshotHash());
            run.setProposedContentJson(json.writeNode(result.response().proposedContent()));
            run.setProposedRenderedText(result.response().proposedRenderedText()); run.setStartedAt(LocalDateTime.now()); run.setCompletedAt(LocalDateTime.now());
            try { runs.insert(run); }
            catch (DataIntegrityViolationException conflict) {
                ResumeTailorRunEntity concurrent = byIdempotency(userId, idempotencyKey);
                if (concurrent != null) return sameRequest(userId, concurrent, requestHash);
                throw conflict;
            }
            int order = 0;
            for (AiTailorChange item : result.response().changes()) {
                ResumeTailorChangeEntity entity = new ResumeTailorChangeEntity();
                entity.setTailorRunId(run.getId()); entity.setSectionType(item.sectionType()); entity.setOperation(item.operation());
                entity.setBeforeText(item.before()); entity.setAfterText(item.after()); entity.setReasonText(item.reason());
                entity.setEvidenceRefsJson(json.write(item.evidenceRefs())); entity.setSortOrder(order++); changes.insert(entity);
            }
            audit.record(userId, "RESUME_TAILOR_RUN_CREATE", "RESUME_TAILOR_RUN", run.getPublicId());
            return view(userId, run);
        } finally {
            redis.delete(lockKey);
        }
    }

    public TailorRunView get(Long userId, String publicId) { return view(userId, owned(userId, publicId)); }

    @Transactional
    public TailorRunView approve(Long userId, String publicId, TailorApproveRequest request) {
        ResumeTailorRunEntity run = owned(userId, publicId);
        if (run.getApprovedResumeVersionId() != null) return view(userId, run);
        if (!"READY".equals(run.getStatus()) || !"VERIFIED".equals(run.getTruthCheckStatus())) {
            throw new BusinessException(4096003, "Only a VERIFIED READY Tailor Run can be approved", HttpStatus.CONFLICT);
        }
        JobEntity job = references.job(userId, publicJobId(run.getJobId()));
        ResumeEntity target = run.getTargetResumeId() == null ? null : references.resumeById(userId, run.getTargetResumeId());
        if (target == null) {
            target = references.resume(userId, resumes.create(userId, new ResumeCreateRequest(
                    job.getTitle() + " Tailored", job.getTitle(), false, false,
                    "Approved evidence-bound version for " + job.getTitle(), "ACTIVE")).resume().id());
            run.setTargetResumeId(target.getId());
        }
        List<ResumeTailorChangeEntity> items = changeEntities(run.getId());
        List<SectionRequest> versionSections = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ResumeTailorChangeEntity item : items) {
            if (!seen.add(item.getSectionType())) continue;
            JsonNode content = json.readNode(json.write(Map.of("text", item.getAfterText(), "reason", item.getReasonText(),
                    "evidenceRefs", json.readStringList(item.getEvidenceRefsJson()))));
            versionSections.add(new SectionRequest(ResumeSectionType.valueOf(item.getSectionType()), content, item.getSortOrder()));
        }
        ResumeVersionEntity base = references.versionById(userId, run.getBaseResumeVersionId());
        ResumeVersionView created = resumes.createTailoredVersion(userId, target.getPublicId(), new TailoredVersionCommand(
                base.getPublicId(), job.getPublicId(), run.getPromptTemplateId(), run.getAiCallId(), run.getPromptVersion(),
                run.getModelName(), request.versionName(), json.readNode(run.getProposedContentJson()),
                run.getProposedRenderedText(), versionSections));
        ResumeVersionEntity persisted = references.version(userId, created.id());
        run.setApprovedResumeVersionId(persisted.getId()); run.setStatus("APPROVED"); run.setApprovedAt(LocalDateTime.now());
        runs.updateById(run); audit.record(userId, "RESUME_TAILOR_APPROVE", "RESUME_TAILOR_RUN", publicId);
        return view(userId, run);
    }

    private TailorRunView view(Long userId, ResumeTailorRunEntity run) {
        JobEntity job = references.job(userId, publicJobId(run.getJobId()));
        CompanyEntity company = references.company(job.getCompanyId());
        ResumeVersionEntity base = references.versionById(userId, run.getBaseResumeVersionId());
        ResumeEntity target = references.resumeById(userId, run.getTargetResumeId());
        ResumeVersionEntity approved = run.getApprovedResumeVersionId() == null ? null : references.versionById(userId, run.getApprovedResumeVersionId());
        List<TailorChangeView> changeViews = changeEntities(run.getId()).stream().map(item -> new TailorChangeView(
                item.getPublicId(), item.getSectionType(), item.getOperation(), item.getBeforeText(), item.getAfterText(),
                item.getReasonText(), json.readStringList(item.getEvidenceRefsJson()), item.getSortOrder())).toList();
        return new TailorRunView(run.getPublicId(), job.getPublicId(), job.getTitle(),
                company == null ? null : company.getDisplayName(), base.getPublicId(), target == null ? null : target.getPublicId(),
                approved == null ? null : approved.getPublicId(), run.getStrategy(), run.getStatus(), run.getExecutionMode(),
                run.getPromptVersion(), run.getModelName(), run.getTruthCheckStatus(), json.readNode(run.getProposedContentJson()),
                run.getProposedRenderedText(), changeViews, run.getStartedAt(), run.getCompletedAt(), run.getApprovedAt(),
                run.getVersion(), run.getCreatedAt());
    }

    private List<ResumeTailorChangeEntity> changeEntities(Long runId) {
        return changes.selectList(new LambdaQueryWrapper<ResumeTailorChangeEntity>()
                .eq(ResumeTailorChangeEntity::getTailorRunId, runId).orderByAsc(ResumeTailorChangeEntity::getSortOrder));
    }

    private ResumeTailorRunEntity owned(Long userId, String publicId) {
        ResumeTailorRunEntity run = runs.selectOne(new LambdaQueryWrapper<ResumeTailorRunEntity>()
                .eq(ResumeTailorRunEntity::getUserId, userId).eq(ResumeTailorRunEntity::getPublicId, publicId).last("LIMIT 1"));
        if (run == null) throw new ResourceNotFoundException("Resume Tailor Run");
        return run;
    }

    private ResumeTailorRunEntity byIdempotency(Long userId, String key) {
        return runs.selectOne(new LambdaQueryWrapper<ResumeTailorRunEntity>()
                .eq(ResumeTailorRunEntity::getUserId, userId).eq(ResumeTailorRunEntity::getIdempotencyKey, key.trim()).last("LIMIT 1"));
    }

    private TailorRunView sameRequest(Long userId, ResumeTailorRunEntity run, String hash) {
        if (!hash.equals(run.getRequestHash())) throw new BusinessException(4096004, "Idempotency-Key was used with different Tailor input", HttpStatus.CONFLICT);
        return view(userId, run);
    }

    private String publicJobId(Long id) {
        JobEntity entity = references.jobByDatabaseId(id);
        if (entity == null) throw new ResourceNotFoundException("Job");
        return entity.getPublicId();
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) throw new ValidationException("Valid Idempotency-Key is required");
    }
}

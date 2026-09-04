package com.jobpilot.application.service;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.domain.RecruiterEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private final ApplicationMapper mapper;
    private final ApplicationLogMapper logMapper;
    private final ApplicationQueueItemMapper queueMapper;
    private final ApplicationQueueService queueService;
    private final ApplicationReferenceService references;
    private final PlatformPolicyService policies;
    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final AuditService audit;

    public ApplicationService(ApplicationMapper mapper, ApplicationLogMapper logMapper,
                              ApplicationQueueItemMapper queueMapper, ApplicationQueueService queueService,
                              ApplicationReferenceService references, PlatformPolicyService policies,
                              StringRedisTemplate redis, JsonCodec json, AuditService audit) {
        this.mapper = mapper;
        this.logMapper = logMapper;
        this.queueMapper = queueMapper;
        this.queueService = queueService;
        this.references = references;
        this.policies = policies;
        this.redis = redis;
        this.json = json;
        this.audit = audit;
    }

    public ApplicationPage list(Long userId, String status) {
        LambdaQueryWrapper<ApplicationEntity> query = new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId)
                .orderByDesc(ApplicationEntity::getLastStatusAt).orderByDesc(ApplicationEntity::getId);
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase();
            if (!ApplicationStateMachine.STATUSES.contains(normalized)) {
                throw new ValidationException("Unsupported application status");
            }
            query.eq(ApplicationEntity::getStatus, normalized);
        }
        List<ApplicationEntity> entities = mapper.selectList(query);
        Map<String, Long> counts = mapper.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                        .eq(ApplicationEntity::getUserId, userId)).stream()
                .collect(Collectors.groupingBy(ApplicationEntity::getStatus,
                        LinkedHashMap::new, Collectors.counting()));
        return new ApplicationPage(entities.stream().map(entity -> view(userId, entity, false)).toList(),
                entities.size(), counts);
    }

    @Transactional
    public ApplicationView create(Long userId, String idempotencyKey, ApplicationCreateRequest request) {
        validateKey(idempotencyKey);
        if (!Boolean.TRUE.equals(request.confirmedExternalSubmission())) {
            throw new ValidationException("Explicit external submission confirmation is required");
        }
        String requestHash = sha256(json.write(request));
        ApplicationEntity existing = byIdempotency(userId, idempotencyKey);
        if (existing != null) return sameHash(userId, existing, requestHash);
        String lockKey = "jobpilot:idempotency:application:" + userId + ":" + sha256(idempotencyKey);
        boolean locked = Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(lockKey, TraceContext.getTraceId(), Duration.ofSeconds(15)));
        if (!locked) {
            existing = byIdempotency(userId, idempotencyKey);
            if (existing != null) return sameHash(userId, existing, requestHash);
            throw new BusinessException(4095202, "Application confirmation is already in progress", HttpStatus.CONFLICT);
        }
        try {
            String mode = request.mode().toUpperCase();
            ApplicationQueueItemEntity queue = request.queueItemId() == null || request.queueItemId().isBlank()
                    ? null : queueService.owned(userId, request.queueItemId());
            JobEntity job;
            ResumeVersionEntity resume;
            if (queue != null) {
                if (!Set.of("APPROVED", "PREPARED").contains(queue.getStatus())) {
                    throw new BusinessException(4095203, "Queue item must be approved before confirmation", HttpStatus.CONFLICT);
                }
                if (!queue.getMode().equals(mode)) throw new ValidationException("Application mode must match queue mode");
                job = references.jobById(userId, queue.getJobId());
                resume = references.resumeVersionById(userId, queue.getResumeVersionId());
            } else {
                if (request.jobId() == null || request.resumeVersionId() == null) {
                    throw new ValidationException("jobId and resumeVersionId are required without queueItemId");
                }
                job = references.job(userId, request.jobId());
                resume = references.resumeVersion(userId, request.resumeVersionId());
            }
            JobSourceEntity source = references.source(userId, job.getId());
            policies.requireMode(source == null ? "UNKNOWN" : source.getPlatform(), mode);
            RecruiterEntity recruiter = references.recruiter(userId, request.recruiterId(), true);
            ApplicationEntity active = active(userId, job.getId());
            if (active != null) {
                if (queue != null && queue.getId().equals(active.getQueueItemId())) return view(userId, active, true);
                throw new BusinessException(4095204, "Job already has an active application", HttpStatus.CONFLICT);
            }
            LocalDateTime occurredAt = request.appliedAt() == null ? LocalDateTime.now() : request.appliedAt();
            ApplicationEntity application = new ApplicationEntity();
            application.setUserId(userId);
            application.setJobId(job.getId());
            application.setJobSourceId(source == null ? null : source.getId());
            application.setResumeVersionId(resume.getId());
            application.setRecruiterId(recruiter == null ? null : recruiter.getId());
            application.setQueueItemId(queue == null ? null : queue.getId());
            application.setStatus("APPLIED");
            application.setApplicationMode(mode);
            application.setAppliedAt(occurredAt);
            application.setLastStatusAt(occurredAt);
            application.setExternalApplicationId(trim(request.externalApplicationId()));
            application.setSourceUrlSnapshot(request.sourceUrlSnapshot() == null && source != null
                    ? source.getJobUrl() : trim(request.sourceUrlSnapshot()));
            application.setNotes(trim(request.notes()));
            application.setIdempotencyKey(idempotencyKey.trim());
            application.setRequestHash(requestHash);
            try {
                mapper.insert(application);
            } catch (DataIntegrityViolationException conflict) {
                ApplicationEntity concurrent = byIdempotency(userId, idempotencyKey);
                if (concurrent != null) return sameHash(userId, concurrent, requestHash);
                throw conflict;
            }
            appendLog(application, null, "APPLIED", "APPLICATION_CREATED", occurredAt,
                    "USER", userId, "User confirmed external submission", null);
            if (queue != null) {
                QueueStateMachine.require(queue.getStatus(), "SUCCESS");
                queue.setStatus("SUCCESS");
                queueMapper.updateById(queue);
            }
            audit.record(userId, "APPLICATION_CREATE", "APPLICATION", application.getPublicId());
            return view(userId, application, true);
        } finally {
            redis.delete(lockKey);
        }
    }

    public ApplicationView get(Long userId, String publicId) {
        return view(userId, owned(userId, publicId), true);
    }

    @Transactional
    public ApplicationView update(Long userId, String publicId, ApplicationUpdateRequest request) {
        ApplicationEntity application = owned(userId, publicId);
        requireVersion(application, request.version());
        if (request.recruiterId() != null) {
            RecruiterEntity recruiter = references.recruiter(userId, request.recruiterId(), true);
            application.setRecruiterId(recruiter == null ? null : recruiter.getId());
        }
        if (request.notes() != null) application.setNotes(trim(request.notes()));
        mapper.updateById(application);
        audit.record(userId, "APPLICATION_UPDATE", "APPLICATION", publicId);
        return get(userId, publicId);
    }

    @Transactional
    public ApplicationView transition(Long userId, String publicId, TransitionRequest request) {
        ApplicationEntity application = owned(userId, publicId);
        requireVersion(application, request.version());
        String to = request.toStatus().toUpperCase();
        ApplicationStateMachine.require(application.getStatus(), to);
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        if (occurredAt.isBefore(application.getLastStatusAt())) {
            throw new ValidationException("Transition time cannot be before the latest status time");
        }
        String from = application.getStatus();
        application.setStatus(to);
        application.setLastStatusAt(occurredAt);
        mapper.updateById(application);
        appendLog(application, from, to, "STATUS_TRANSITION", occurredAt,
                request.source(), userId, trim(request.note()), request.evidence());
        audit.record(userId, "APPLICATION_STATUS_TRANSITION", "APPLICATION", publicId);
        return get(userId, publicId);
    }

    public List<ApplicationLogView> logs(Long userId, String publicId) {
        return logs(owned(userId, publicId));
    }

    public ApplicationKpis kpis(Long userId) {
        List<ApplicationQueueItemEntity> queue = queueMapper.selectList(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId));
        List<ApplicationEntity> applications = mapper.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId));
        List<ApplicationLogEntity> logs = logMapper.selectList(new LambdaQueryWrapper<ApplicationLogEntity>()
                .eq(ApplicationLogEntity::getUserId, userId));
        return new ApplicationKpis(
                queue.size(),
                queue.stream().filter(q -> !QueueStateMachine.TERMINAL.contains(q.getStatus())).count(),
                queue.stream().filter(q -> Set.of("NEED_REVIEW", "APPROVED", "PREPARED", "BLOCKED").contains(q.getStatus())).count(),
                applications.size(), reached(logs, Set.of("APPLIED")), reached(logs, Set.of("VIEWED")),
                reached(logs, Set.of("REPLIED")), reached(logs, Set.of("WRITTEN_TEST")),
                reached(logs, Set.of("INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3", "HR_INTERVIEW")),
                reached(logs, Set.of("OFFER")), applications.stream()
                        .filter(a -> ApplicationStateMachine.TERMINAL.contains(a.getStatus())).count());
    }

    public ApplicationEntity owned(Long userId, String publicId) {
        ApplicationEntity application = mapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId)
                .eq(ApplicationEntity::getPublicId, publicId).last("LIMIT 1"));
        if (application == null) throw new ResourceNotFoundException("Application");
        return application;
    }

    public ApplicationEntity ownedById(Long userId, Long id) {
        ApplicationEntity application = mapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getId, id).last("LIMIT 1"));
        if (application == null) throw new ResourceNotFoundException("Application");
        return application;
    }

    private ApplicationView view(Long userId, ApplicationEntity application, boolean includeTimeline) {
        JobEntity job = references.jobById(userId, application.getJobId());
        JobSourceEntity source = references.source(userId, application.getJobId());
        ResumeVersionEntity resume = references.resumeVersionById(userId, application.getResumeVersionId());
        RecruiterEntity recruiter = references.recruiterById(userId, application.getRecruiterId());
        ApplicationQueueItemEntity queue = application.getQueueItemId() == null ? null
                : queueMapper.selectById(application.getQueueItemId());
        List<String> allowed = ApplicationStateMachine.allowedTransitions()
                .getOrDefault(application.getStatus(), Set.of()).stream().sorted().toList();
        return new ApplicationView(application.getPublicId(), references.jobView(job, source),
                references.resumeView(resume), recruiter == null ? null : recruiter.getPublicId(),
                queue == null ? null : queue.getPublicId(), application.getStatus(),
                application.getApplicationMode(), application.getAppliedAt(), application.getLastStatusAt(),
                application.getExternalApplicationId(), application.getSourceUrlSnapshot(), application.getNotes(),
                application.getVersion(), application.getCreatedAt(), application.getUpdatedAt(),
                includeTimeline ? logs(application) : List.of(), allowed);
    }

    private List<ApplicationLogView> logs(ApplicationEntity application) {
        return logMapper.selectList(new LambdaQueryWrapper<ApplicationLogEntity>()
                        .eq(ApplicationLogEntity::getUserId, application.getUserId())
                        .eq(ApplicationLogEntity::getApplicationId, application.getId())
                        .orderByAsc(ApplicationLogEntity::getOccurredAt).orderByAsc(ApplicationLogEntity::getId))
                .stream().map(log -> new ApplicationLogView(log.getPublicId(), log.getFromStatus(),
                        log.getToStatus(), log.getEventType(), log.getOccurredAt(), log.getSource(), log.getNote(),
                        json.readNode(log.getEvidenceJson()), log.getTraceId(), log.getCreatedAt())).toList();
    }

    private void appendLog(ApplicationEntity application, String from, String to, String type,
                           LocalDateTime occurredAt, String source, Long actor, String note, JsonNode evidence) {
        ApplicationLogEntity log = new ApplicationLogEntity();
        log.setApplicationId(application.getId());
        log.setUserId(application.getUserId());
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setEventType(type);
        log.setOccurredAt(occurredAt);
        log.setSource(source);
        log.setActorUserId(actor);
        log.setNote(note);
        log.setEvidenceJson(json.writeNode(evidence));
        log.setTraceId(TraceContext.getTraceId());
        logMapper.insert(log);
    }

    private ApplicationEntity active(Long userId, Long jobId) {
        return mapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getJobId, jobId)
                .notIn(ApplicationEntity::getStatus, ApplicationStateMachine.TERMINAL).last("LIMIT 1"));
    }

    private ApplicationEntity byIdempotency(Long userId, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId)
                .eq(ApplicationEntity::getIdempotencyKey, key.trim()).last("LIMIT 1"));
    }

    private ApplicationView sameHash(Long userId, ApplicationEntity application, String hash) {
        if (!hash.equals(application.getRequestHash())) {
            throw new BusinessException(4095205,
                    "Idempotency-Key was already used with different input", HttpStatus.CONFLICT);
        }
        return view(userId, application, true);
    }

    private void requireVersion(ApplicationEntity entity, Integer version) {
        if (!entity.getVersion().equals(version)) {
            throw new BusinessException(4095206, "Application version conflict", HttpStatus.CONFLICT);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new ValidationException("Valid Idempotency-Key is required");
        }
    }

    private static long reached(List<ApplicationLogEntity> logs, Set<String> statuses) {
        return logs.stream().filter(log -> statuses.contains(log.getToStatus()))
                .map(ApplicationLogEntity::getApplicationId).distinct().count();
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

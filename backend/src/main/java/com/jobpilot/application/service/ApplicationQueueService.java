package com.jobpilot.application.service;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationQueueService {
    private final ApplicationQueueItemMapper mapper;
    private final ApplicationReferenceService references;
    private final PlatformPolicyService policies;
    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final AuditService audit;

    public ApplicationQueueService(ApplicationQueueItemMapper mapper,
                                   ApplicationReferenceService references,
                                   PlatformPolicyService policies,
                                   StringRedisTemplate redis, JsonCodec json, AuditService audit) {
        this.mapper = mapper;
        this.references = references;
        this.policies = policies;
        this.redis = redis;
        this.json = json;
        this.audit = audit;
    }

    public List<QueueItemView> list(Long userId, String status) {
        LambdaQueryWrapper<ApplicationQueueItemEntity> query = new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId)
                .orderByDesc(ApplicationQueueItemEntity::getPriority)
                .orderByAsc(ApplicationQueueItemEntity::getScheduledAt)
                .orderByDesc(ApplicationQueueItemEntity::getCreatedAt);
        if (status != null && !status.isBlank()) query.eq(ApplicationQueueItemEntity::getStatus, status.toUpperCase());
        return mapper.selectList(query).stream().map(item -> view(userId, item)).toList();
    }

    @Transactional
    public QueueItemView enqueue(Long userId, String idempotencyKey, QueueEnqueueRequest request) {
        validateKey(idempotencyKey);
        String requestHash = sha256(json.write(request));
        ApplicationQueueItemEntity idempotent = byIdempotency(userId, idempotencyKey);
        if (idempotent != null) return requireSameHash(userId, idempotent, requestHash);

        String lockKey = "jobpilot:idempotency:application-queue:" + userId + ":" + sha256(idempotencyKey);
        boolean locked = Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(lockKey, TraceContext.getTraceId(), Duration.ofSeconds(15)));
        if (!locked) {
            idempotent = byIdempotency(userId, idempotencyKey);
            if (idempotent != null) return requireSameHash(userId, idempotent, requestHash);
            throw new BusinessException(4095102, "Queue request is already in progress", HttpStatus.CONFLICT);
        }
        try {
            JobEntity job = references.job(userId, request.jobId());
            JobSourceEntity source = references.source(userId, job.getId());
            String platform = source == null ? "UNKNOWN" : source.getPlatform();
            String mode = request.mode().toUpperCase();
            policies.requireMode(platform, mode);
            JobMatchEntity match = references.match(userId, job.getId(), request.jobMatchId());
            if (match == null && !Boolean.TRUE.equals(request.allowUnevaluated())) {
                throw new ValidationException("A successful match is required unless allowUnevaluated is true");
            }
            ResumeVersionEntity resume = references.resumeVersion(userId, request.resumeVersionId());
            ApplicationQueueItemEntity active = active(userId, job.getId());
            if (active != null) {
                if (active.getResumeVersionId().equals(resume.getId()) && active.getMode().equals(mode)) return view(userId, active);
                throw new BusinessException(4095103, "Job already has an active queue item", HttpStatus.CONFLICT);
            }
            ApplicationQueueItemEntity item = new ApplicationQueueItemEntity();
            item.setUserId(userId);
            item.setJobId(job.getId());
            item.setJobMatchId(match == null ? null : match.getId());
            item.setResumeVersionId(resume.getId());
            item.setGreetingReference(trim(request.greetingReference()));
            item.setMode(mode);
            item.setStatus(match == null ? "NEED_REVIEW" : "READY");
            item.setPriority(request.priority() == null ? 50 : request.priority());
            item.setScheduledAt(request.scheduledAt());
            item.setIdempotencyKey(idempotencyKey.trim());
            item.setRequestHash(requestHash);
            try {
                mapper.insert(item);
            } catch (DataIntegrityViolationException conflict) {
                ApplicationQueueItemEntity concurrent = byIdempotency(userId, idempotencyKey);
                if (concurrent != null) return requireSameHash(userId, concurrent, requestHash);
                throw conflict;
            }
            audit.record(userId, "APPLICATION_QUEUE_ENQUEUE", "APPLICATION_QUEUE_ITEM", item.getPublicId());
            return view(userId, item);
        } finally {
            redis.delete(lockKey);
        }
    }

    @Transactional
    public QueueBatchResult enqueueBatch(Long userId, String idempotencyKey, QueueBatchRequest request) {
        validateKey(idempotencyKey);
        List<QueueItemView> items = new ArrayList<>();
        int reused = 0;
        for (int index = 0; index < request.items().size(); index++) {
            QueueEnqueueRequest item = request.items().get(index);
            String childKey = idempotencyKey + ":" + index;
            if (byIdempotency(userId, childKey) != null) reused++;
            items.add(enqueue(userId, childKey, item));
        }
        int unique = new LinkedHashSet<>(items.stream().map(QueueItemView::id).toList()).size();
        return new QueueBatchResult(items, request.items().size(), unique, reused);
    }

    @Transactional
    public QueueItemView update(Long userId, String publicId, QueueUpdateRequest request) {
        ApplicationQueueItemEntity item = owned(userId, publicId);
        requireVersion(item, request.version());
        if (QueueStateMachine.TERMINAL.contains(item.getStatus())) {
            throw new BusinessException(4095104, "Terminal queue item cannot be edited", HttpStatus.CONFLICT);
        }
        if (request.resumeVersionId() != null && !request.resumeVersionId().isBlank()) {
            item.setResumeVersionId(references.resumeVersion(userId, request.resumeVersionId()).getId());
        }
        if (request.greetingReference() != null) item.setGreetingReference(trim(request.greetingReference()));
        if (request.priority() != null) item.setPriority(request.priority());
        if (request.scheduledAt() != null) item.setScheduledAt(request.scheduledAt());
        mapper.updateById(item);
        audit.record(userId, "APPLICATION_QUEUE_UPDATE", "APPLICATION_QUEUE_ITEM", publicId);
        return view(userId, owned(userId, publicId));
    }

    @Transactional
    public QueueItemView approve(Long userId, String publicId, VersionRequest request) {
        ApplicationQueueItemEntity item = owned(userId, publicId);
        requireVersion(item, request.version());
        if ("APPROVED".equals(item.getStatus()) || "PREPARED".equals(item.getStatus())) return view(userId, item);
        QueueStateMachine.require(item.getStatus(), "APPROVED");
        item.setStatus("APPROVED");
        item.setApprovedAt(LocalDateTime.now());
        item.setApprovedBy(userId);
        mapper.updateById(item);
        audit.record(userId, "APPLICATION_QUEUE_APPROVE", "APPLICATION_QUEUE_ITEM", publicId);
        return view(userId, owned(userId, publicId));
    }

    @Transactional
    public QueueItemView skip(Long userId, String publicId, VersionRequest request) {
        ApplicationQueueItemEntity item = owned(userId, publicId);
        requireVersion(item, request.version());
        if ("SKIPPED".equals(item.getStatus())) return view(userId, item);
        QueueStateMachine.require(item.getStatus(), "SKIPPED");
        item.setStatus("SKIPPED");
        mapper.updateById(item);
        audit.record(userId, "APPLICATION_QUEUE_SKIP", "APPLICATION_QUEUE_ITEM", publicId);
        return view(userId, owned(userId, publicId));
    }

    @Transactional
    public AssistPrepareView prepare(Long userId, String publicId, VersionRequest request) {
        ApplicationQueueItemEntity item = owned(userId, publicId);
        requireVersion(item, request.version());
        if (!"ASSIST".equals(item.getMode())) throw new ValidationException("Only ASSIST queue items can be prepared");
        JobSourceEntity source = references.source(userId, item.getJobId());
        policies.requireMode(source == null ? "UNKNOWN" : source.getPlatform(), "ASSIST");
        if (!"PREPARED".equals(item.getStatus())) {
            QueueStateMachine.require(item.getStatus(), "PREPARED");
            item.setStatus("PREPARED");
            item.setPreparedAt(LocalDateTime.now());
            mapper.updateById(item);
            audit.record(userId, "APPLICATION_ASSIST_PREPARE", "APPLICATION_QUEUE_ITEM", publicId);
            item = owned(userId, publicId);
        }
        return new AssistPrepareView(view(userId, item), false, false, true,
                "Open the job yourself, submit manually, then explicitly confirm the application in JobPilot",
                List.of("未打开任何外部网站", "未提交任何表单", "未创建申请记录"));
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        ApplicationQueueItemEntity item = owned(userId, publicId);
        if ("SUCCESS".equals(item.getStatus())) {
            throw new BusinessException(4095105, "Successful queue history cannot be removed", HttpStatus.CONFLICT);
        }
        mapper.deleteById(item.getId());
        audit.record(userId, "APPLICATION_QUEUE_REMOVE", "APPLICATION_QUEUE_ITEM", publicId);
    }

    public ApplicationQueueItemEntity owned(Long userId, String publicId) {
        ApplicationQueueItemEntity item = mapper.selectOne(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId)
                .eq(ApplicationQueueItemEntity::getPublicId, publicId).last("LIMIT 1"));
        if (item == null) throw new ResourceNotFoundException("Application queue item");
        return item;
    }

    public QueueItemView view(Long userId, ApplicationQueueItemEntity item) {
        JobEntity job = references.jobById(userId, item.getJobId());
        JobSourceEntity source = references.source(userId, item.getJobId());
        ResumeVersionEntity resume = references.resumeVersionById(userId, item.getResumeVersionId());
        String platform = source == null ? "UNKNOWN" : source.getPlatform();
        return new QueueItemView(item.getPublicId(), references.jobView(job, source),
                references.matchPublicId(userId, item.getJobMatchId()),
                references.resumeView(resume), item.getGreetingReference(), item.getMode(), item.getStatus(),
                item.getPriority(), item.getScheduledAt(), item.getApprovedAt(), item.getPreparedAt(),
                item.getLastErrorCode(), item.getLastErrorMessage(), item.getVersion(),
                item.getCreatedAt(), item.getUpdatedAt(), policies.resolve(platform));
    }

    private ApplicationQueueItemEntity active(Long userId, Long jobId) {
        return mapper.selectOne(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId)
                .eq(ApplicationQueueItemEntity::getJobId, jobId)
                .in(ApplicationQueueItemEntity::getStatus, "WAITING", "READY", "NEED_REVIEW", "APPROVED", "PREPARED", "BLOCKED")
                .last("LIMIT 1"));
    }

    private ApplicationQueueItemEntity byIdempotency(Long userId, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId)
                .eq(ApplicationQueueItemEntity::getIdempotencyKey, key.trim()).last("LIMIT 1"));
    }

    private QueueItemView requireSameHash(Long userId, ApplicationQueueItemEntity entity, String hash) {
        if (!entity.getRequestHash().equals(hash)) {
            throw new BusinessException(4095106, "Idempotency-Key was already used with different input", HttpStatus.CONFLICT);
        }
        return view(userId, entity);
    }

    private void requireVersion(ApplicationQueueItemEntity item, Integer version) {
        if (!item.getVersion().equals(version)) {
            throw new BusinessException(4095107, "Queue item version conflict", HttpStatus.CONFLICT);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw new ValidationException("Valid Idempotency-Key is required");
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

package com.jobpilot.automation.service;

import static com.jobpilot.automation.dto.AutomationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.domain.PlatformPolicyEntity;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.application.mapper.PlatformPolicyMapper;
import com.jobpilot.application.service.PlatformPolicyService;
import com.jobpilot.application.service.QueueStateMachine;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.automation.config.AutomationProperties;
import com.jobpilot.automation.domain.AutomationTaskEntity;
import com.jobpilot.automation.domain.AutomationTaskStepEntity;
import com.jobpilot.automation.mapper.AutomationTaskMapper;
import com.jobpilot.automation.mapper.AutomationTaskStepMapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.mapper.CandidateProfileMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.common.util.UlidGenerator;
import com.jobpilot.extension.domain.ExtensionDeviceEntity;
import com.jobpilot.extension.mapper.ExtensionDeviceMapper;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.mapper.JobSourceMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AutomationTaskService {
    private static final Set<String> STEP_TYPES = Set.of("OPEN", "CHECK", "FILL", "HANDOFF", "BLOCKED");
    private static final Set<String> STEP_STATUSES = Set.of("SUCCEEDED", "BLOCKED");
    private static final Set<String> SELECTORS = Set.of("#jp-name", "#jp-email", "#jp-phone", "#jp-summary");
    private final AutomationTaskMapper tasks;
    private final AutomationTaskStepMapper steps;
    private final ApplicationQueueItemMapper queueItems;
    private final PlatformPolicyMapper policyMapper;
    private final PlatformPolicyService policies;
    private final JobSourceMapper sources;
    private final CandidateProfileMapper profiles;
    private final ExtensionDeviceMapper devices;
    private final AutomationProperties properties;
    private final AutomationTaskTokenService tokenService;
    private final RestClient worker;
    private final JsonCodec json;
    private final AuditService audit;

    public AutomationTaskService(
            AutomationTaskMapper tasks, AutomationTaskStepMapper steps,
            ApplicationQueueItemMapper queueItems, PlatformPolicyMapper policyMapper,
            PlatformPolicyService policies, JobSourceMapper sources,
            CandidateProfileMapper profiles, ExtensionDeviceMapper devices,
            AutomationProperties properties, AutomationTaskTokenService tokenService,
            RestClient.Builder restClientBuilder, JsonCodec json, AuditService audit) {
        this.tasks = tasks;
        this.steps = steps;
        this.queueItems = queueItems;
        this.policyMapper = policyMapper;
        this.policies = policies;
        this.sources = sources;
        this.profiles = profiles;
        this.devices = devices;
        this.properties = properties;
        this.tokenService = tokenService;
        this.worker = restClientBuilder.baseUrl(properties.getWorkerBaseUrl()).build();
        this.json = json;
        this.audit = audit;
    }

    public AutomationTaskView prepare(Long userId, String devicePublicId, String queuePublicId, String idempotencyKey) {
        requireEnabled();
        validateKey(idempotencyKey);
        String requestHash = sha256(queuePublicId + "|" + devicePublicId);
        AutomationTaskEntity previous = byKey(userId, idempotencyKey);
        if (previous != null) return sameRequest(userId, previous, requestHash);

        ExtensionDeviceEntity device = ownedDevice(userId, devicePublicId);
        ApplicationQueueItemEntity queue = ownedQueue(userId, queuePublicId);
        if (!"ASSIST".equals(queue.getMode()) || !"APPROVED".equals(queue.getStatus())) {
            throw new BusinessException(4097101,
                    "Assist preparation requires an APPROVED ASSIST queue item", HttpStatus.CONFLICT);
        }
        JobSourceEntity source = sources.selectOne(new LambdaQueryWrapper<JobSourceEntity>()
                .eq(JobSourceEntity::getUserId, userId)
                .eq(JobSourceEntity::getJobId, queue.getJobId())
                .orderByDesc(JobSourceEntity::getId).last("LIMIT 1"));
        if (source == null || source.getJobUrl() == null || source.getJobUrl().isBlank()) {
            throw new ValidationException("Queue job must have an HTTP(S) source URL");
        }
        requireAllowedTarget(source.getJobUrl());
        policies.requireMode(source.getPlatform(), "ASSIST");
        PlatformPolicyEntity policy = policyMapper.selectOne(new LambdaQueryWrapper<PlatformPolicyEntity>()
                .eq(PlatformPolicyEntity::getPlatform, normalized(source.getPlatform())).last("LIMIT 1"));
        if (policy == null) throw new ValidationException("Configured platform policy is required");
        CandidateProfileEntity profile = profiles.selectOne(new LambdaQueryWrapper<CandidateProfileEntity>()
                .eq(CandidateProfileEntity::getUserId, userId).last("LIMIT 1"));
        if (profile == null) throw new ResourceNotFoundException("Candidate profile");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusSeconds(Math.max(60, properties.getTaskExpirationSeconds()));
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setPublicId(UlidGenerator.next());
        task.setUserId(userId);
        task.setExtensionDeviceId(device.getId());
        task.setQueueItemId(queue.getId());
        task.setPlatformPolicyId(policy.getId());
        task.setIdempotencyKey(idempotencyKey.trim());
        task.setRequestHash(requestHash);
        task.setExpiresAt(expiry);
        task.setTargetUrl(source.getJobUrl());
        task.setTaskType("ASSIST_PREPARE");
        task.setStatus("RUNNING");
        task.setExternallySubmitted(false);
        task.setApplicationCreated(false);
        task.setFinalConfirmationRequired(true);
        task.setStartedAt(now);
        String taskToken = tokenService.create(task.getPublicId(), expiry.atZone(ZoneId.systemDefault()).toInstant(),
                queue.getPublicId(), "ASSIST_ALLOWED");
        task.setTaskTokenHash(sha256(taskToken));
        try {
            tasks.insert(task);
        } catch (DataIntegrityViolationException conflict) {
            AutomationTaskEntity concurrent = byKey(userId, idempotencyKey);
            if (concurrent != null) return sameRequest(userId, concurrent, requestHash);
            throw conflict;
        }

        WorkerPrepareRequest request = new WorkerPrepareRequest(
                "assist-prepare-request-v1", task.getPublicId(), taskToken, task.getTargetUrl(),
                queue.getPublicId(), "ASSIST_ALLOWED", true, profileFields(profile));
        try {
            WorkerPrepareResponse response = worker.post().uri("/internal/v1/assist/prepare")
                    .header("Authorization", "Bearer " + properties.getWorkerToken())
                    .header("X-Trace-Id", TraceContext.getTraceId())
                    .body(request).retrieve().body(WorkerPrepareResponse.class);
            applyResult(task, queue, response);
        } catch (RestClientException exception) {
            fail(task, "WORKER_UNAVAILABLE", "Automation Worker request failed");
            throw new BusinessException(5027102,
                    "Automation Worker is unavailable or rejected the task", HttpStatus.BAD_GATEWAY);
        } catch (BusinessException exception) {
            fail(task, "SAFETY_BOUNDARY_VIOLATION", exception.getMessage());
            throw exception;
        }
        audit.record(userId, "AUTOMATION_ASSIST_PREPARE", "AUTOMATION_TASK", task.getPublicId());
        return get(userId, task.getPublicId());
    }

    public AutomationTaskView get(Long userId, String publicId) {
        AutomationTaskEntity task = tasks.selectOne(new LambdaQueryWrapper<AutomationTaskEntity>()
                .eq(AutomationTaskEntity::getUserId, userId)
                .eq(AutomationTaskEntity::getPublicId, publicId).last("LIMIT 1"));
        if (task == null) throw new ResourceNotFoundException("Automation task");
        return view(task);
    }

    private void applyResult(AutomationTaskEntity task, ApplicationQueueItemEntity queue,
                             WorkerPrepareResponse response) {
        if (response == null || !"assist-prepare-response-v1".equals(response.schemaVersion())
                || !task.getPublicId().equals(response.taskId())
                || response.externallySubmitted() || response.applicationCreated()
                || !response.finalConfirmationRequired() || response.submitCount() != 0
                || !("PREPARED".equals(response.status()) || "BLOCKED".equals(response.status()))) {
            throw new BusinessException(5027103, "Automation Worker violated the safe response contract",
                    HttpStatus.BAD_GATEWAY);
        }
        List<WorkerStep> workerSteps = response.steps() == null ? List.of() : response.steps();
        workerSteps.forEach(this::validateStep);
        int sequence = 0;
        for (WorkerStep workerStep : workerSteps) {
            AutomationTaskStepEntity step = new AutomationTaskStepEntity();
            step.setTaskId(task.getId());
            step.setSequenceNo(++sequence);
            step.setStepType(workerStep.type());
            step.setStatus(workerStep.status());
            step.setSelectorHint(workerStep.selector());
            step.setDetailJson(json.write(Map.of("detail", safe(workerStep.detail(), 800))));
            step.setOccurredAt(LocalDateTime.now());
            step.setTraceId(TraceContext.getTraceId());
            steps.insert(step);
        }
        task.setStatus(response.status());
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorCode("BLOCKED".equals(response.status()) ? safe(response.blockedReason(), 80) : null);
        task.setErrorMessage("BLOCKED".equals(response.status()) ? "Worker stopped for required human review" : null);
        tasks.updateById(task);

        QueueStateMachine.require(queue.getStatus(), response.status());
        queue.setStatus(response.status());
        if ("PREPARED".equals(response.status())) queue.setPreparedAt(LocalDateTime.now());
        else {
            queue.setLastErrorCode(safe(response.blockedReason(), 80));
            queue.setLastErrorMessage("Assist preparation stopped before external submission");
        }
        queueItems.updateById(queue);
    }

    private void validateStep(WorkerStep step) {
        if (step == null || !STEP_TYPES.contains(step.type()) || !STEP_STATUSES.contains(step.status())
                || (step.selector() != null && !SELECTORS.contains(step.selector()))) {
            throw new BusinessException(5027104, "Automation Worker returned an unsafe step",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private void fail(AutomationTaskEntity task, String code, String message) {
        if ("FAILED".equals(task.getStatus())) return;
        task.setStatus("FAILED");
        task.setErrorCode(code);
        task.setErrorMessage(safe(message, 1000));
        task.setCompletedAt(LocalDateTime.now());
        tasks.updateById(task);
        AutomationTaskStepEntity step = new AutomationTaskStepEntity();
        step.setTaskId(task.getId());
        step.setSequenceNo(1);
        step.setStepType("ERROR");
        step.setStatus("FAILED");
        step.setDetailJson(json.write(Map.of("detail", "Worker task ended without external submission")));
        step.setOccurredAt(LocalDateTime.now());
        step.setTraceId(TraceContext.getTraceId());
        steps.insert(step);
    }

    private AutomationTaskView view(AutomationTaskEntity task) {
        ApplicationQueueItemEntity queue = queueItems.selectById(task.getQueueItemId());
        ExtensionDeviceEntity device = task.getExtensionDeviceId() == null ? null : devices.selectById(task.getExtensionDeviceId());
        List<AutomationStepView> stepViews = steps.selectList(new LambdaQueryWrapper<AutomationTaskStepEntity>()
                        .eq(AutomationTaskStepEntity::getTaskId, task.getId())
                        .orderByAsc(AutomationTaskStepEntity::getSequenceNo)).stream()
                .map(item -> new AutomationStepView(item.getPublicId(), item.getSequenceNo(), item.getStepType(),
                        item.getStatus(), item.getSelectorHint(), json.readNode(item.getDetailJson()).path("detail").asText(),
                        item.getOccurredAt(), item.getTraceId())).toList();
        return new AutomationTaskView(task.getPublicId(), queue == null ? null : queue.getPublicId(),
                device == null ? null : device.getPublicId(), task.getTaskType(), task.getStatus(),
                task.getTargetUrl(), false, false, true, task.getErrorCode(), task.getErrorMessage(),
                task.getExpiresAt(), task.getStartedAt(), task.getCompletedAt(), task.getCreatedAt(), stepViews);
    }

    private List<WorkerField> profileFields(CandidateProfileEntity profile) {
        List<WorkerField> result = new ArrayList<>();
        add(result, "#jp-name", profile.getFullName(), 200);
        add(result, "#jp-email", profile.getEmail(), 320);
        add(result, "#jp-phone", profile.getPhone(), 60);
        add(result, "#jp-summary", profile.getSummary(), 4000);
        return List.copyOf(result);
    }

    private void add(List<WorkerField> result, String selector, String value, int max) {
        if (value != null && !value.isBlank()) result.add(new WorkerField(selector, safe(value.trim(), max)));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new BusinessException(5037101,
                "Automation Worker is disabled by default", HttpStatus.SERVICE_UNAVAILABLE);
        if (properties.getWorkerToken() == null || properties.getWorkerToken().length() < 24) {
            throw new BusinessException(5037102, "Automation Worker token is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void requireAllowedTarget(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (host == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || properties.getAllowedTargetHosts().stream().map(item -> item.toLowerCase(Locale.ROOT))
                    .noneMatch(item -> item.equals(host.toLowerCase(Locale.ROOT)))) {
                throw new ValidationException("Automation target is not on the explicit local allowlist");
            }
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Automation target URL is invalid");
        }
    }

    private ExtensionDeviceEntity ownedDevice(Long userId, String publicId) {
        ExtensionDeviceEntity device = devices.selectOne(new LambdaQueryWrapper<ExtensionDeviceEntity>()
                .eq(ExtensionDeviceEntity::getUserId, userId)
                .eq(ExtensionDeviceEntity::getPublicId, publicId)
                .eq(ExtensionDeviceEntity::getStatus, "ACTIVE").last("LIMIT 1"));
        if (device == null) throw new ResourceNotFoundException("Active extension device");
        return device;
    }

    private ApplicationQueueItemEntity ownedQueue(Long userId, String publicId) {
        ApplicationQueueItemEntity queue = queueItems.selectOne(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId)
                .eq(ApplicationQueueItemEntity::getPublicId, publicId).last("LIMIT 1"));
        if (queue == null) throw new ResourceNotFoundException("Application queue item");
        return queue;
    }

    private AutomationTaskEntity byKey(Long userId, String key) {
        return tasks.selectOne(new LambdaQueryWrapper<AutomationTaskEntity>()
                .eq(AutomationTaskEntity::getUserId, userId)
                .eq(AutomationTaskEntity::getIdempotencyKey, key.trim()).last("LIMIT 1"));
    }

    private AutomationTaskView sameRequest(Long userId, AutomationTaskEntity task, String requestHash) {
        if (!task.getRequestHash().equals(requestHash)) throw new BusinessException(4097105,
                "Idempotency-Key was already used with different input", HttpStatus.CONFLICT);
        return get(userId, task.getPublicId());
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ValidationException("Valid Idempotency-Key is required");
        }
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value, int max) {
        if (value == null) return null;
        String result = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

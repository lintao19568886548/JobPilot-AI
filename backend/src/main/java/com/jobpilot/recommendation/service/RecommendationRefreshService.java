package com.jobpilot.recommendation.service;

import static com.jobpilot.recommendation.dto.RecommendationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunRequest;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunView;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.matching.service.MatchService;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.domain.RecommendationRefreshItemEntity;
import com.jobpilot.recommendation.domain.RecommendationRefreshRunEntity;
import com.jobpilot.recommendation.mapper.RecommendationRefreshItemMapper;
import com.jobpilot.recommendation.mapper.RecommendationRefreshRunMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationRefreshService {
    private final RecommendationRefreshRunMapper refreshMapper;
    private final RecommendationRefreshItemMapper itemMapper;
    private final JobMapper jobMapper;
    private final MatchRunMapper matchRunMapper;
    private final MatchService matchService;
    private final RecommendationProjectionService projection;
    private final RecommendationProperties properties;
    private final JsonCodec json;
    private final AuditService audit;
    private final StringRedisTemplate redis;

    public RecommendationRefreshService(RecommendationRefreshRunMapper refreshMapper,
                                        RecommendationRefreshItemMapper itemMapper,
                                        JobMapper jobMapper, MatchRunMapper matchRunMapper,
                                        MatchService matchService,
                                        RecommendationProjectionService projection,
                                        RecommendationProperties properties, JsonCodec json,
                                        AuditService audit, StringRedisTemplate redis) {
        this.refreshMapper = refreshMapper;
        this.itemMapper = itemMapper;
        this.jobMapper = jobMapper;
        this.matchRunMapper = matchRunMapper;
        this.matchService = matchService;
        this.projection = projection;
        this.properties = properties;
        this.json = json;
        this.audit = audit;
        this.redis = redis;
    }

    public RecommendationRefreshRunView start(Long userId, RecommendationRefreshRequest request,
                                               String headerKey) {
        String key = firstNonBlank(headerKey, request.idempotencyKey(), "recommendation-refresh-" + UUID.randomUUID());
        boolean force = Boolean.TRUE.equals(request.force());
        String requestHash = sha256(json.write(Map.of(
                "onlyUnevaluated", Boolean.TRUE.equals(request.onlyUnevaluated()),
                "city", safe(request.city()), "level", safe(request.level()),
                "updatedAfter", request.updatedAfter() == null ? "" : request.updatedAfter().toString(),
                "force", force, "limit", request.limit() == null ? properties.getBatchMaxSize() : request.limit())));
        RecommendationRefreshRunEntity same = refreshMapper.selectOne(
                new LambdaQueryWrapper<RecommendationRefreshRunEntity>()
                        .eq(RecommendationRefreshRunEntity::getUserId, userId)
                        .eq(RecommendationRefreshRunEntity::getIdempotencyKey, key).last("LIMIT 1"));
        if (same != null) {
            if (!same.getRequestHash().equals(requestHash)) {
                throw new BusinessException(4094002,
                        "Idempotency key was already used with different refresh criteria", HttpStatus.CONFLICT);
            }
            return view(synchronize(same));
        }

        String lockKey = "jobpilot:phase4:refresh:create:" + userId + ":" + key;
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(4094003, "Recommendation refresh is already being created", HttpStatus.CONFLICT);
        }
        try {
            List<JobEntity> eligible = eligible(userId, request);
            int requestedLimit = request.limit() == null ? properties.getBatchMaxSize() : request.limit();
            int limit = Math.max(1, Math.min(properties.getBatchMaxSize(), requestedLimit));
            if (eligible.size() > limit) eligible = eligible.subList(0, limit);

            RecommendationRefreshRunEntity refresh = new RecommendationRefreshRunEntity();
            refresh.setUserId(userId);
            refresh.setStatus("PENDING");
            refresh.setIdempotencyKey(key);
            refresh.setRequestHash(requestHash);
            refresh.setCriteriaJson(json.write(request));
            refresh.setForceRun(force);
            refresh.setTotalCount(eligible.size());
            refresh.setSubmittedCount(0);
            refresh.setReusedCount(0);
            refresh.setSucceededCount(0);
            refresh.setFailedCount(0);
            refresh.setStartedAt(LocalDateTime.now());
            refreshMapper.insert(refresh);

            int submitted = 0;
            int reused = 0;
            int submissionFailures = 0;
            for (JobEntity job : eligible) {
                try {
                    String matchKey = "rec-refresh:" + refresh.getPublicId() + ":" + job.getPublicId();
                    MatchRunView matchView = matchService.start(userId, job.getPublicId(),
                            new MatchRunRequest(null, matchKey, force), matchKey);
                    MatchRunEntity matchRun = matchRunMapper.selectOne(new LambdaQueryWrapper<MatchRunEntity>()
                            .eq(MatchRunEntity::getUserId, userId)
                            .eq(MatchRunEntity::getPublicId, matchView.id()).last("LIMIT 1"));
                    boolean wasReused = "SUCCEEDED".equals(matchView.status());
                    RecommendationRefreshItemEntity item = new RecommendationRefreshItemEntity();
                    item.setRefreshRunId(refresh.getId());
                    item.setUserId(userId);
                    item.setJobId(job.getId());
                    item.setMatchRunId(matchRun.getId());
                    item.setStatus(matchView.status());
                    item.setReused(wasReused);
                    itemMapper.insert(item);
                    if (wasReused) reused++; else submitted++;
                } catch (RuntimeException exception) {
                    submissionFailures++;
                }
            }
            refresh.setSubmittedCount(submitted);
            refresh.setReusedCount(reused);
            refresh.setSucceededCount(reused);
            refresh.setFailedCount(submissionFailures);
            if (eligible.isEmpty() || reused + submissionFailures == eligible.size()) {
                refresh.setStatus(submissionFailures == 0 ? "SUCCEEDED"
                        : reused > 0 ? "PARTIAL_SUCCESS" : "FAILED");
                refresh.setFinishedAt(LocalDateTime.now());
            } else {
                refresh.setStatus("RUNNING");
            }
            refreshMapper.updateById(refresh);
            projection.synchronize(userId);
            audit.record(userId, "RECOMMENDATION_REFRESH_CREATE", "RECOMMENDATION_REFRESH_RUN", refresh.getPublicId());
            return view(refresh);
        } finally {
            redis.delete(lockKey);
        }
    }

    public RecommendationRefreshRunView get(Long userId, String publicId) {
        return view(synchronize(owned(userId, publicId)));
    }

    @Transactional
    public RecommendationRefreshRunView retryFailed(Long userId, String publicId) {
        RecommendationRefreshRunEntity refresh = synchronize(owned(userId, publicId));
        List<RecommendationRefreshItemEntity> items = items(refresh.getId());
        int retried = 0;
        for (RecommendationRefreshItemEntity item : items) {
            if (!List.of("FAILED", "DEAD").contains(item.getStatus())) continue;
            MatchRunEntity matchRun = matchRunMapper.selectById(item.getMatchRunId());
            if (matchRun == null || !List.of("FAILED", "DEAD").contains(matchRun.getStatus())) continue;
            matchService.retry(userId, matchRun.getPublicId());
            item.setStatus("PENDING");
            item.setErrorMessageSafe(null);
            itemMapper.updateById(item);
            retried++;
        }
        if (retried == 0) {
            throw new BusinessException(4094004, "Refresh run has no retryable failed match items", HttpStatus.CONFLICT);
        }
        refresh.setStatus("RUNNING");
        refresh.setFinishedAt(null);
        refresh.setErrorMessageSafe(null);
        refreshMapper.updateById(refresh);
        audit.record(userId, "RECOMMENDATION_REFRESH_RETRY", "RECOMMENDATION_REFRESH_RUN", refresh.getPublicId());
        return view(refresh);
    }

    public void synchronizeRunning() {
        refreshMapper.selectList(new LambdaQueryWrapper<RecommendationRefreshRunEntity>()
                        .in(RecommendationRefreshRunEntity::getStatus, List.of("PENDING", "RUNNING"))
                        .orderByAsc(RecommendationRefreshRunEntity::getId).last("LIMIT 20"))
                .forEach(this::synchronize);
    }

    public RecommendationRefreshRunEntity synchronize(RecommendationRefreshRunEntity refresh) {
        List<RecommendationRefreshItemEntity> items = items(refresh.getId());
        int submissionFailures = Math.max(0, refresh.getTotalCount() - items.size());
        int succeeded = 0;
        int dead = 0;
        for (RecommendationRefreshItemEntity item : items) {
            MatchRunEntity matchRun = matchRunMapper.selectById(item.getMatchRunId());
            if (matchRun == null) {
                item.setStatus("DEAD");
                item.setErrorMessageSafe("Match run is unavailable");
                dead++;
            } else {
                if (!matchRun.getStatus().equals(item.getStatus())
                        || !java.util.Objects.equals(matchRun.getErrorMessageSafe(), item.getErrorMessageSafe())) {
                    item.setStatus(matchRun.getStatus());
                    item.setErrorMessageSafe(matchRun.getErrorMessageSafe());
                    itemMapper.updateById(item);
                }
                if ("SUCCEEDED".equals(matchRun.getStatus())) succeeded++;
                if ("DEAD".equals(matchRun.getStatus())) dead++;
            }
        }
        int failed = submissionFailures + dead;
        refresh.setSucceededCount(succeeded);
        refresh.setFailedCount(failed);
        if (succeeded + failed >= refresh.getTotalCount()) {
            refresh.setStatus(failed == 0 ? "SUCCEEDED" : succeeded > 0 ? "PARTIAL_SUCCESS" : "FAILED");
            refresh.setFinishedAt(LocalDateTime.now());
            refresh.setErrorMessageSafe(failed == 0 ? null : "One or more match items failed safely");
        } else {
            refresh.setStatus("RUNNING");
        }
        refreshMapper.updateById(refresh);
        projection.synchronize(refresh.getUserId());
        return refresh;
    }

    private List<JobEntity> eligible(Long userId, RecommendationRefreshRequest request) {
        Map<Long, JobRecommendationEntity> recommendations = projection.synchronize(userId).stream()
                .collect(Collectors.toMap(JobRecommendationEntity::getJobId, Function.identity()));
        Map<Long, JobMatchEntity> matches = projection.latestMatches(userId);
        return jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
                        .eq(JobEntity::getUserId, userId)
                        .in(JobEntity::getStatus, List.of("ACTIVE", "PARSED"))
                        .in(JobEntity::getParseStatus, List.of("SUCCESS", "PARTIAL"))
                        .orderByDesc(JobEntity::getUpdatedAt).orderByDesc(JobEntity::getId))
                .stream()
                .filter(job -> recommendations.get(job.getId()) == null
                        || !"IGNORED".equals(recommendations.get(job.getId()).getRecommendationStatus()))
                .filter(job -> !Boolean.TRUE.equals(request.onlyUnevaluated())
                        || recommendations.get(job.getId()) == null
                        || recommendations.get(job.getId()).getLatestMatchId() == null)
                .filter(job -> !has(request.city()) || contains(job.getCity(), request.city()))
                .filter(job -> request.updatedAfter() == null || !job.getUpdatedAt().isBefore(request.updatedAfter()))
                .filter(job -> !has(request.level()) || matches.get(job.getId()) != null
                        && request.level().equalsIgnoreCase(matches.get(job.getId()).getLevel()))
                .toList();
    }

    private RecommendationRefreshRunEntity owned(Long userId, String publicId) {
        RecommendationRefreshRunEntity run = refreshMapper.selectOne(
                new LambdaQueryWrapper<RecommendationRefreshRunEntity>()
                        .eq(RecommendationRefreshRunEntity::getUserId, userId)
                        .eq(RecommendationRefreshRunEntity::getPublicId, publicId).last("LIMIT 1"));
        if (run == null) throw new ResourceNotFoundException("Recommendation refresh run");
        return run;
    }

    private List<RecommendationRefreshItemEntity> items(Long refreshId) {
        return itemMapper.selectList(new LambdaQueryWrapper<RecommendationRefreshItemEntity>()
                .eq(RecommendationRefreshItemEntity::getRefreshRunId, refreshId)
                .orderByAsc(RecommendationRefreshItemEntity::getId));
    }

    private RecommendationRefreshRunView view(RecommendationRefreshRunEntity refresh) {
        List<RecommendationRefreshItemEntity> entities = items(refresh.getId());
        Map<Long, JobEntity> jobs = entities.isEmpty() ? Map.of() : jobMapper.selectBatchIds(entities.stream()
                        .map(RecommendationRefreshItemEntity::getJobId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(JobEntity::getId, Function.identity()));
        Map<Long, MatchRunEntity> runs = entities.isEmpty() ? Map.of() : matchRunMapper.selectBatchIds(entities.stream()
                        .map(RecommendationRefreshItemEntity::getMatchRunId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MatchRunEntity::getId, Function.identity()));
        List<RecommendationRefreshItemView> itemViews = entities.stream().map(item -> {
            JobEntity job = jobs.get(item.getJobId());
            MatchRunEntity matchRun = runs.get(item.getMatchRunId());
            return new RecommendationRefreshItemView(item.getPublicId(),
                    job == null ? null : job.getPublicId(), matchRun == null ? null : matchRun.getPublicId(),
                    item.getStatus(), Boolean.TRUE.equals(item.getReused()), item.getErrorMessageSafe());
        }).toList();
        return new RecommendationRefreshRunView(refresh.getPublicId(), refresh.getStatus(),
                Boolean.TRUE.equals(refresh.getForceRun()), refresh.getTotalCount(), refresh.getSubmittedCount(),
                refresh.getReusedCount(), refresh.getSucceededCount(), refresh.getFailedCount(), itemViews,
                refresh.getStartedAt(), refresh.getFinishedAt(), refresh.getErrorMessageSafe(), refresh.getCreatedAt());
    }

    private static boolean has(String value) { return value != null && !value.isBlank(); }
    private static boolean contains(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (has(value)) return value.trim();
        return UUID.randomUUID().toString();
    }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

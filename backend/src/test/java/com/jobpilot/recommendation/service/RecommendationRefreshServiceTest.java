package com.jobpilot.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.matching.service.MatchService;
import com.jobpilot.recommendation.domain.RecommendationRefreshRunEntity;
import com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationRefreshRequest;
import com.jobpilot.recommendation.mapper.RecommendationRefreshItemMapper;
import com.jobpilot.recommendation.mapper.RecommendationRefreshRunMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RecommendationRefreshServiceTest {
    private RecommendationRefreshRunMapper refreshMapper;
    private RecommendationRefreshItemMapper itemMapper;
    private RecommendationProjectionService projection;
    private AuditService audit;
    private RecommendationRefreshService service;
    private AtomicReference<RecommendationRefreshRunEntity> stored;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "recommendation-refresh-test"),
                JobEntity.class);
        refreshMapper = mock(RecommendationRefreshRunMapper.class);
        itemMapper = mock(RecommendationRefreshItemMapper.class);
        projection = mock(RecommendationProjectionService.class);
        audit = mock(AuditService.class);
        JobMapper jobMapper = mock(JobMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);
        when(jobMapper.selectList(any())).thenReturn(List.of());
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(projection.synchronize(42L)).thenReturn(List.of());
        when(projection.latestMatches(42L)).thenReturn(java.util.Map.of());
        stored = new AtomicReference<>();
        when(refreshMapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        when(refreshMapper.insert(any(RecommendationRefreshRunEntity.class))).thenAnswer(invocation -> {
            RecommendationRefreshRunEntity entity = invocation.getArgument(0);
            entity.setId(1L); entity.setPublicId("refresh_1"); entity.setVersion(0);
            entity.setCreatedAt(java.time.LocalDateTime.now()); stored.set(entity); return 1;
        });
        when(refreshMapper.updateById(any(RecommendationRefreshRunEntity.class))).thenReturn(1);
        RecommendationProperties properties = new RecommendationProperties();
        properties.setBatchMaxSize(100);
        service = new RecommendationRefreshService(refreshMapper, itemMapper, jobMapper,
                mock(MatchRunMapper.class), mock(MatchService.class), projection, properties,
                new JsonCodec(new ObjectMapper()), audit, redis);
    }

    @Test
    void emptyEligibleBatchCompletesSuccessfullyAndIsAudited() {
        var result = service.start(42L, request("batch-empty", false), null);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.totalCount()).isZero();
        verify(audit).record(42L, "RECOMMENDATION_REFRESH_CREATE", "RECOMMENDATION_REFRESH_RUN", "refresh_1");
    }

    @Test
    void identicalIdempotencyKeyAndPayloadReturnsTheExistingRun() {
        var first = service.start(42L, request("batch-same", false), null);
        var second = service.start(42L, request("batch-same", false), null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentCriteria() {
        service.start(42L, request("batch-conflict", false), null);

        assertThatThrownBy(() -> service.start(42L, request("batch-conflict", true), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("different refresh criteria");
    }

    @Test
    void retryRejectsRunsWithoutFailedItems() {
        RecommendationRefreshRunEntity run = new RecommendationRefreshRunEntity();
        run.setId(2L); run.setPublicId("refresh_done"); run.setUserId(42L); run.setStatus("SUCCEEDED");
        run.setTotalCount(0); run.setSubmittedCount(0); run.setReusedCount(0);
        run.setSucceededCount(0); run.setFailedCount(0); run.setForceRun(false);
        run.setCreatedAt(java.time.LocalDateTime.now()); stored.set(run);

        assertThatThrownBy(() -> service.retryFailed(42L, "refresh_done"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no retryable");
    }

    private RecommendationRefreshRequest request(String key, boolean force) {
        return new RecommendationRefreshRequest(true, null, null, null, force, 20, key);
    }
}

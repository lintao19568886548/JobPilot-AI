package com.jobpilot.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSkillMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.service.MatchService;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.mapper.RecommendationEventMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RecommendationServiceTest {
    private RecommendationProjectionService projection;
    private JobRecommendationMapper recommendationMapper;
    private RecommendationEventMapper eventMapper;
    private JobMapper jobMapper;
    private JobSourceMapper sourceMapper;
    private JobSkillMapper jobSkillMapper;
    private AuditService audit;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "recommendation-service-test");
        TableInfoHelper.initTableInfo(assistant, JobEntity.class);
        TableInfoHelper.initTableInfo(assistant, JobSkillEntity.class);
        projection = mock(RecommendationProjectionService.class);
        recommendationMapper = mock(JobRecommendationMapper.class);
        eventMapper = mock(RecommendationEventMapper.class);
        jobMapper = mock(JobMapper.class);
        sourceMapper = mock(JobSourceMapper.class);
        jobSkillMapper = mock(JobSkillMapper.class);
        audit = mock(AuditService.class);
        service = new RecommendationService(projection, recommendationMapper, eventMapper, jobMapper,
                mock(CompanyMapper.class), sourceMapper, jobSkillMapper, mock(SkillMapper.class),
                mock(MatchService.class), new JsonCodec(new ObjectMapper().findAndRegisterModules()), audit,
                mock(StringRedisTemplate.class));
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(jobSkillMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void capabilityContractEnablesPhaseFiveApplicationTracking() {
        assertThat(service.capabilities().applicationTracking()).isTrue();
        assertThat(service.capabilities().applicationPhase()).isEqualTo("PHASE_5");
    }

    @Test
    void rejectsUnknownSortAndOversizedFilter() {
        assertThatThrownBy(() -> service.list(42L, query("ALL", "SQL_DROP", 20, null)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("sort");
        var tooLong = new com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationQuery(
                "ALL", null, null, null, "x".repeat(121), null, null, null, null,
                null, null, null, null, null, null, null, 20, "AI_RECOMMENDED");
        assertThatThrownBy(() -> service.list(42L, tooLong)).isInstanceOf(ValidationException.class);
    }

    @Test
    void topViewSafelyExcludesRejectedMatchWithNullLevel() {
        JobRecommendationEntity rec = recommendation(1L, 7L, "rec_1");
        JobEntity job = job(7L, "job_1");
        JobMatchEntity rejected = match(21L, 7L, null);
        stubSnapshot(List.of(rec), List.of(job), Map.of(7L, rejected));

        assertThatCode(() -> service.list(42L, query("TOP", "AI_RECOMMENDED", 20, null)))
                .doesNotThrowAnyException();
        assertThat(service.list(42L, query("TOP", "AI_RECOMMENDED", 20, null)).items()).isEmpty();
    }

    @Test
    void cursorPaginationIsStableAndDoesNotDuplicateRows() {
        JobRecommendationEntity one = recommendation(1L, 7L, "rec_1"); one.setRankScore(BigDecimal.valueOf(90));
        JobRecommendationEntity two = recommendation(2L, 8L, "rec_2"); two.setRankScore(BigDecimal.valueOf(80));
        stubSnapshot(List.of(one, two), List.of(job(7L, "job_1"), job(8L, "job_2")), Map.of());

        var first = service.list(42L, query("ALL", "MATCH_DESC", 1, null));
        var second = service.list(42L, query("ALL", "MATCH_DESC", 1, first.nextCursor()));

        assertThat(first.hasMore()).isTrue();
        assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void favoriteIsIdempotentAndDoesNotDuplicateEvent() {
        JobRecommendationEntity rec = recommendation(1L, 7L, "rec_1"); rec.setFavorite(true);
        stubSnapshot(List.of(rec), List.of(job(7L, "job_1")), Map.of());
        when(recommendationMapper.selectOne(any())).thenReturn(rec);

        assertThat(service.favorite(42L, "rec_1", 0).favorite()).isTrue();
        verify(eventMapper, never()).insert(any(com.jobpilot.recommendation.domain.RecommendationEventEntity.class));
        verify(recommendationMapper, never()).updateById(any(JobRecommendationEntity.class));
    }

    @Test
    void favoriteStateChangeWritesOneImmutableEventAndAudit() {
        JobRecommendationEntity rec = recommendation(1L, 7L, "rec_1");
        stubSnapshot(List.of(rec), List.of(job(7L, "job_1")), Map.of());
        when(recommendationMapper.selectOne(any())).thenReturn(rec);
        when(recommendationMapper.updateById(rec)).thenReturn(1);

        assertThat(service.favorite(42L, "rec_1", 0).favorite()).isTrue();

        verify(eventMapper).insert(any(com.jobpilot.recommendation.domain.RecommendationEventEntity.class));
        verify(audit).record(42L, "RECOMMENDATION_FAVORITE", "RECOMMENDATION", "rec_1");
    }

    @Test
    void emptyDatasetDoesNotIssueAnEmptyCompanyBatchQuery() {
        when(projection.synchronize(42L)).thenReturn(List.of());
        when(jobMapper.selectList(any())).thenReturn(List.of());
        when(projection.latestMatches(42L)).thenReturn(Map.of());

        assertThat(service.snapshot(42L)).isEmpty();
    }

    private void stubSnapshot(List<JobRecommendationEntity> recs, List<JobEntity> jobs,
                              Map<Long, JobMatchEntity> matches) {
        when(projection.synchronize(42L)).thenReturn(recs);
        when(jobMapper.selectList(any())).thenReturn(jobs);
        when(projection.latestMatches(42L)).thenReturn(matches);
    }

    private JobRecommendationEntity recommendation(long id, long jobId, String publicId) {
        JobRecommendationEntity rec = new JobRecommendationEntity();
        rec.setId(id); rec.setPublicId(publicId); rec.setUserId(42L); rec.setJobId(jobId);
        rec.setRecommendationStatus("READY"); rec.setFavorite(false); rec.setVersion(0);
        rec.setRankVersion("recommendation-v1"); rec.setRankBasisJson("{}");
        rec.setCreatedAt(LocalDateTime.now()); rec.setUpdatedAt(LocalDateTime.now());
        return rec;
    }

    private JobEntity job(long id, String publicId) {
        JobEntity job = new JobEntity();
        job.setId(id); job.setPublicId(publicId); job.setUserId(42L); job.setTitle("Java Engineer " + id);
        job.setStatus("ACTIVE"); job.setParseStatus("SUCCESS"); job.setCity("Hangzhou");
        job.setFirstCollectedAt(LocalDateTime.now()); job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    private JobMatchEntity match(long id, long jobId, String level) {
        JobMatchEntity match = new JobMatchEntity();
        match.setId(id); match.setPublicId("match_" + id); match.setJobId(jobId); match.setUserId(42L);
        match.setStatus("SUCCEEDED"); match.setHardFilterResult("PASS");
        match.setOverallScore(BigDecimal.valueOf(88)); match.setLevel(level);
        match.setRecommendation(level == null ? "REJECTED" : "RECOMMEND");
        match.setEvaluatedAt(LocalDateTime.now());
        return match;
    }

    private com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationQuery query(
            String view, String sort, Integer limit, String cursor) {
        return new com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationQuery(
                view, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, cursor, limit, sort);
    }
}

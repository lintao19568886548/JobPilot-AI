package com.jobpilot.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.learning.mapper.LtrModelVersionMapper;
import com.jobpilot.learning.service.LearningRankingService;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationProjectionServiceTest {
    private JobMapper jobMapper;
    private JobMatchMapper matchMapper;
    private MatchRunMapper runMapper;
    private JobRecommendationMapper recommendationMapper;
    private RecommendationProjectionService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(JobMapper.class);
        matchMapper = mock(JobMatchMapper.class);
        runMapper = mock(MatchRunMapper.class);
        recommendationMapper = mock(JobRecommendationMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        LearningRankingService learningRanking = new LearningRankingService(
                mock(LtrModelVersionMapper.class), objectMapper, jsonCodec);
        service = new RecommendationProjectionService(jobMapper, matchMapper, runMapper, recommendationMapper,
                jsonCodec, learningRanking);
    }

    @Test
    void derivesReadyOnlyFromAnImmutableMatch() {
        assertThat(service.derivedStatus(match(81L), null)).isEqualTo("READY");
    }

    @Test
    void derivesFailureFromTerminalRunWithoutInventingAScore() {
        MatchRunEntity run = new MatchRunEntity(); run.setStatus("DEAD");
        assertThat(service.derivedStatus(null, run)).isEqualTo("MATCH_FAILED");
    }

    @Test
    void derivesUnevaluatedWhenThereIsNoMatchEvidence() {
        assertThat(service.derivedStatus(null, null)).isEqualTo("UNEVALUATED");
    }

    @Test
    void latestMatchProjectionKeepsTheFirstRowFromDescendingQuery() {
        JobMatchEntity latest = match(91L); latest.setJobId(7L); latest.setPublicId("match_latest");
        JobMatchEntity older = match(55L); older.setJobId(7L); older.setPublicId("match_old");
        when(matchMapper.selectList(any())).thenReturn(List.of(latest, older));

        assertThat(service.latestMatches(42L).get(7L).getPublicId()).isEqualTo("match_latest");
    }

    @Test
    void synchronizeWithNoJobsDoesNotCreateSyntheticRecommendations() {
        when(jobMapper.selectList(any())).thenReturn(List.of());
        when(recommendationMapper.selectList(any())).thenReturn(List.of());
        when(matchMapper.selectList(any())).thenReturn(List.of());
        when(runMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.synchronize(42L)).isEmpty();
        verify(recommendationMapper, org.mockito.Mockito.never()).insert(any(com.jobpilot.recommendation.domain.JobRecommendationEntity.class));
    }

    @Test
    void mysqlJsonKeyNormalizationDoesNotCauseProjectionVersionChurn() {
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        JobEntity job = new JobEntity(); job.setId(7L); job.setUserId(42L);
        JobMatchEntity latest = match(88L); latest.setId(21L); latest.setPublicId("match_21");
        latest.setJobId(7L); latest.setRecommendation("RECOMMEND"); latest.setLevel("A"); latest.setEvaluatedAt(evaluatedAt);
        com.jobpilot.recommendation.domain.JobRecommendationEntity rec =
                new com.jobpilot.recommendation.domain.JobRecommendationEntity();
        rec.setId(1L); rec.setUserId(42L); rec.setJobId(7L); rec.setLatestMatchId(21L);
        rec.setRecommendationStatus("READY"); rec.setFavorite(false); rec.setRankScore(BigDecimal.valueOf(88));
        rec.setRankVersion(RecommendationProjectionService.RANK_VERSION); rec.setLastEvaluatedAt(evaluatedAt);
        rec.setRankBasisJson("{\"evaluatedAt\":\"2026-09-01T12:00:00\",\"level\":\"A\",\"matchId\":\"match_21\",\"overallScore\":88,\"recommendation\":\"RECOMMEND\",\"source\":\"LATEST_IMMUTABLE_MATCH\",\"version\":\"recommendation-v1\"}");
        when(jobMapper.selectList(any())).thenReturn(List.of(job));
        when(recommendationMapper.selectList(any())).thenReturn(List.of(rec));
        when(matchMapper.selectList(any())).thenReturn(List.of(latest));
        when(runMapper.selectList(any())).thenReturn(List.of());

        service.synchronize(42L);

        verify(recommendationMapper, org.mockito.Mockito.never()).updateById(
                any(com.jobpilot.recommendation.domain.JobRecommendationEntity.class));
    }

    private JobMatchEntity match(long score) {
        JobMatchEntity match = new JobMatchEntity();
        match.setOverallScore(BigDecimal.valueOf(score));
        match.setEvaluatedAt(LocalDateTime.now());
        return match;
    }
}

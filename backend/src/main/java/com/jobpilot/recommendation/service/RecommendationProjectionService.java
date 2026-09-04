package com.jobpilot.recommendation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.learning.domain.LtrModelVersionEntity;
import com.jobpilot.learning.service.LearningRankingService;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationProjectionService {
    public static final String RANK_VERSION = "recommendation-v1";

    private final JobMapper jobMapper;
    private final JobMatchMapper matchMapper;
    private final MatchRunMapper runMapper;
    private final JobRecommendationMapper recommendationMapper;
    private final JsonCodec json;
    private final LearningRankingService learningRanking;

    public RecommendationProjectionService(JobMapper jobMapper, JobMatchMapper matchMapper,
                                           MatchRunMapper runMapper,
                                           JobRecommendationMapper recommendationMapper,
                                           JsonCodec json, LearningRankingService learningRanking) {
        this.jobMapper = jobMapper;
        this.matchMapper = matchMapper;
        this.runMapper = runMapper;
        this.recommendationMapper = recommendationMapper;
        this.json = json;
        this.learningRanking = learningRanking;
    }

    @Transactional
    public List<JobRecommendationEntity> synchronize(Long userId) {
        List<JobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).orderByAsc(JobEntity::getId));
        Map<Long, JobRecommendationEntity> recommendations = recommendationMapper.selectList(
                        new LambdaQueryWrapper<JobRecommendationEntity>()
                                .eq(JobRecommendationEntity::getUserId, userId))
                .stream().collect(Collectors.toMap(JobRecommendationEntity::getJobId,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, JobMatchEntity> latestMatches = latestMatches(userId);
        Map<Long, MatchRunEntity> latestRuns = latestRuns(userId);
        LtrModelVersionEntity activeModel = learningRanking.active(userId);

        for (JobEntity job : jobs) {
            JobRecommendationEntity recommendation = recommendations.get(job.getId());
            JobMatchEntity match = latestMatches.get(job.getId());
            MatchRunEntity run = latestRuns.get(job.getId());
            if (recommendation == null) {
                recommendation = new JobRecommendationEntity();
                recommendation.setUserId(userId);
                recommendation.setJobId(job.getId());
                recommendation.setFavorite(false);
                recommendation.setRankVersion(RANK_VERSION);
                recommendation.setIgnoredReason("IGNORED".equals(job.getStatus()) ? "Ignored in Job Center" : null);
                recommendation.setRecommendationStatus("IGNORED".equals(job.getStatus())
                        ? "IGNORED" : derivedStatus(match, run));
                applyMatch(recommendation, match, learningRanking.rank(match, activeModel));
                recommendationMapper.insert(recommendation);
                recommendations.put(job.getId(), recommendation);
                continue;
            }

            String status = "IGNORED".equals(recommendation.getRecommendationStatus())
                    ? "IGNORED" : derivedStatus(match, run);
            Long matchId = match == null ? null : match.getId();
            LearningRankingService.RankDecision rankDecision = learningRanking.rank(match, activeModel);
            BigDecimal rank = rankDecision.score();
            String rankBasis = rankDecision.basisJson();
            boolean changed = !Objects.equals(recommendation.getLatestMatchId(), matchId)
                    || !Objects.equals(recommendation.getRecommendationStatus(), status)
                    || !Objects.equals(recommendation.getRankScore(), rank)
                    || !Objects.equals(recommendation.getRankVersion(), rankDecision.version())
                    || !sameJson(recommendation.getRankBasisJson(), rankBasis)
                    || !Objects.equals(recommendation.getLastEvaluatedAt(), match == null ? null : match.getEvaluatedAt());
            if (changed) {
                recommendation.setRecommendationStatus(status);
                applyMatch(recommendation, match, rankDecision);
                recommendationMapper.updateById(recommendation);
            }
        }
        return recommendationMapper.selectList(new LambdaQueryWrapper<JobRecommendationEntity>()
                .eq(JobRecommendationEntity::getUserId, userId));
    }

    public String derivedStatus(JobMatchEntity match, MatchRunEntity run) {
        if (match != null) return "READY";
        if (run != null && List.of("FAILED", "DEAD").contains(run.getStatus())) return "MATCH_FAILED";
        return "UNEVALUATED";
    }

    public Map<Long, JobMatchEntity> latestMatches(Long userId) {
        Map<Long, JobMatchEntity> result = new LinkedHashMap<>();
        matchMapper.selectList(new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getUserId, userId)
                        .orderByDesc(JobMatchEntity::getEvaluatedAt)
                        .orderByDesc(JobMatchEntity::getId))
                .forEach(match -> result.putIfAbsent(match.getJobId(), match));
        return result;
    }

    public Map<Long, MatchRunEntity> latestRuns(Long userId) {
        Map<Long, MatchRunEntity> result = new LinkedHashMap<>();
        runMapper.selectList(new LambdaQueryWrapper<MatchRunEntity>()
                        .eq(MatchRunEntity::getUserId, userId)
                        .orderByDesc(MatchRunEntity::getCreatedAt)
                        .orderByDesc(MatchRunEntity::getId))
                .forEach(run -> result.putIfAbsent(run.getJobId(), run));
        return result;
    }

    private void applyMatch(JobRecommendationEntity recommendation, JobMatchEntity match, LearningRankingService.RankDecision rank) {
        recommendation.setLatestMatchId(match == null ? null : match.getId());
        recommendation.setRankScore(rank.score());
        recommendation.setRankVersion(rank.version());
        recommendation.setRankBasisJson(rank.basisJson());
        recommendation.setLastEvaluatedAt(match == null ? null : match.getEvaluatedAt());
    }

    private String rankBasis(JobMatchEntity match) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("version", RANK_VERSION);
        basis.put("source", match == null ? "UNEVALUATED" : "LATEST_IMMUTABLE_MATCH");
        if (match != null) {
            basis.put("matchId", match.getPublicId());
            basis.put("recommendation", match.getRecommendation());
            basis.put("overallScore", match.getOverallScore());
            basis.put("level", match.getLevel());
            basis.put("evaluatedAt", match.getEvaluatedAt());
        }
        return json.write(basis);
    }

    private boolean sameJson(String left, String right) {
        if (Objects.equals(left, right)) return true;
        if (left == null || right == null) return false;
        return Objects.equals(json.readNode(left), json.readNode(right));
    }
}

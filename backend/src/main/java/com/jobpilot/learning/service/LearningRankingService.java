package com.jobpilot.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.learning.domain.LtrModelVersionEntity;
import com.jobpilot.learning.mapper.LtrModelVersionMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LearningRankingService {
    public static final String BASELINE_VERSION = "recommendation-v1";
    private final LtrModelVersionMapper models;
    private final ObjectMapper objectMapper;
    private final JsonCodec json;

    public LearningRankingService(LtrModelVersionMapper models, ObjectMapper objectMapper, JsonCodec json) {
        this.models=models; this.objectMapper=objectMapper; this.json=json;
    }

    public record RankDecision(BigDecimal score, String version, String basisJson) { }

    public LtrModelVersionEntity active(Long userId) {
        return models.selectOne(new LambdaQueryWrapper<LtrModelVersionEntity>()
                .eq(LtrModelVersionEntity::getUserId,userId).eq(LtrModelVersionEntity::getStatus,"ACTIVE").last("LIMIT 1"));
    }

    public RankDecision rank(JobMatchEntity match, LtrModelVersionEntity active) {
        if (match == null) {
            Map<String,Object> basis = new LinkedHashMap<>();
            basis.put("version", active == null ? BASELINE_VERSION : "ltr-v" + active.getVersionNo());
            basis.put("source", "UNEVALUATED");
            return new RankDecision(null, active == null ? BASELINE_VERSION : "ltr-v" + active.getVersionNo(), json.write(basis));
        }
        if (active == null) {
            Map<String,Object> basis = new LinkedHashMap<>();
            basis.put("version", BASELINE_VERSION);
            basis.put("source", "LATEST_IMMUTABLE_MATCH");
            basis.put("matchId", match.getPublicId());
            basis.put("recommendation", match.getRecommendation());
            basis.put("overallScore", match.getOverallScore());
            basis.put("level", match.getLevel());
            basis.put("evaluatedAt", match.getEvaluatedAt());
            return new RankDecision(match.getOverallScore(), BASELINE_VERSION, json.write(basis));
        }
        Map<String,BigDecimal> weights=weights(active); BigDecimal score=LearningMath.adjustedScore(features(match),weights,match.getPenaltyScore());
        Map<String,Object> basis=new LinkedHashMap<>();basis.put("source","USER_ACTIVATED_LTR");basis.put("modelId",active.getPublicId());basis.put("modelVersion",active.getVersionNo());basis.put("matchId",match.getPublicId());basis.put("weights",weights);basis.put("score",score);
        return new RankDecision(score,"ltr-v"+active.getVersionNo(),json.write(basis));
    }

    public Map<String,BigDecimal> weights(LtrModelVersionEntity model){
        try { Map<String,Object> root=objectMapper.readValue(model.getParametersJson(),new TypeReference<>(){});Object raw=root.get("weights");return objectMapper.convertValue(raw,new TypeReference<LinkedHashMap<String,BigDecimal>>(){}); }
        catch(Exception exception){throw new IllegalStateException("Stored LTR parameters are invalid",exception);}
    }

    public static Map<String,BigDecimal> features(JobMatchEntity match){
        Map<String,BigDecimal> values=new LinkedHashMap<>();values.put("skill",match.getSkillScore());values.put("embedding",match.getEmbeddingScore());values.put("llm",match.getLlmScore());values.put("project",match.getProjectScore());values.put("preference",match.getPreferenceScore());values.put("company",match.getCompanyScore());return values;
    }
}

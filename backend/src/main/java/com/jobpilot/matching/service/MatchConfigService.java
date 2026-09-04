package com.jobpilot.matching.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.matching.domain.MatchConfigEntity;
import com.jobpilot.matching.dto.MatchingDtos.MatchConfigRequest;
import com.jobpilot.matching.dto.MatchingDtos.MatchConfigView;
import com.jobpilot.matching.mapper.MatchConfigMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchConfigService {
    public static final String ALGORITHM_VERSION = "match-v1";
    private static final Set<String> WEIGHT_KEYS = Set.of("skill", "embedding", "llm", "project", "preference", "company");
    private static final Set<String> LEVEL_KEYS = Set.of("S", "A", "B", "C", "D");
    private final MatchConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final JsonCodec json;
    private final AuditService audit;

    public MatchConfigService(MatchConfigMapper mapper, ObjectMapper objectMapper, JsonCodec json, AuditService audit) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public MatchConfigEntity activeEntity(Long userId) {
        MatchConfigEntity active = mapper.selectOne(new LambdaQueryWrapper<MatchConfigEntity>()
                .eq(MatchConfigEntity::getUserId, userId).eq(MatchConfigEntity::getActive, true).last("LIMIT 1"));
        if (active != null) return active;
        MatchConfigRequest request = new MatchConfigRequest("Default Match Config", defaultWeights(), defaultThresholds(), Map.of("hardFilterMax", new BigDecimal("100")), true);
        create(userId, request);
        return mapper.selectOne(new LambdaQueryWrapper<MatchConfigEntity>()
                .eq(MatchConfigEntity::getUserId, userId).eq(MatchConfigEntity::getActive, true).last("LIMIT 1"));
    }

    @Transactional
    public MatchConfigView create(Long userId, MatchConfigRequest request) {
        validate(request.weights(), request.levelThresholds());
        int nextVersion = mapper.selectList(new LambdaQueryWrapper<MatchConfigEntity>().eq(MatchConfigEntity::getUserId, userId))
                .stream().mapToInt(MatchConfigEntity::getVersionNo).max().orElse(0) + 1;
        if (Boolean.TRUE.equals(request.activate())) deactivateCurrent(userId);
        MatchConfigEntity entity = new MatchConfigEntity();
        entity.setUserId(userId);
        entity.setName(request.name().trim());
        entity.setVersionNo(nextVersion);
        entity.setWeightsJson(json.write(request.weights()));
        entity.setLevelThresholdsJson(json.write(request.levelThresholds()));
        entity.setPenaltiesJson(json.write(request.penalties() == null ? Map.of() : request.penalties()));
        entity.setAlgorithmVersion(ALGORITHM_VERSION);
        entity.setActive(Boolean.TRUE.equals(request.activate()));
        entity.setEffectiveAt(LocalDateTime.now());
        mapper.insert(entity);
        audit.record(userId, "MATCH_CONFIG_CREATE", "MATCH_CONFIG", entity.getPublicId());
        if (entity.getActive()) audit.record(userId, "MATCH_CONFIG_ACTIVATE", "MATCH_CONFIG", entity.getPublicId());
        return view(entity);
    }

    @Transactional
    public MatchConfigView activate(Long userId, String publicId) {
        MatchConfigEntity entity = owned(userId, publicId);
        if (!Boolean.TRUE.equals(entity.getActive())) {
            deactivateCurrent(userId);
            entity.setActive(true);
            entity.setEffectiveAt(LocalDateTime.now());
            mapper.updateById(entity);
            audit.record(userId, "MATCH_CONFIG_ACTIVATE", "MATCH_CONFIG", entity.getPublicId());
        }
        return view(entity);
    }

    public List<MatchConfigView> list(Long userId) {
        if (mapper.selectCount(new LambdaQueryWrapper<MatchConfigEntity>().eq(MatchConfigEntity::getUserId, userId)) == 0) activeEntity(userId);
        return mapper.selectList(new LambdaQueryWrapper<MatchConfigEntity>()
                        .eq(MatchConfigEntity::getUserId, userId).orderByDesc(MatchConfigEntity::getVersionNo))
                .stream().map(this::view).toList();
    }

    public MatchConfigView view(MatchConfigEntity entity) {
        return new MatchConfigView(entity.getPublicId(), entity.getName(), entity.getVersionNo(), map(entity.getWeightsJson()),
                map(entity.getLevelThresholdsJson()), map(entity.getPenaltiesJson()), entity.getAlgorithmVersion(),
                Boolean.TRUE.equals(entity.getActive()), entity.getEffectiveAt(), entity.getCreatedAt());
    }

    public Map<String, BigDecimal> weights(MatchConfigEntity entity) { return map(entity.getWeightsJson()); }
    public Map<String, BigDecimal> thresholds(MatchConfigEntity entity) { return map(entity.getLevelThresholdsJson()); }

    private void validate(Map<String, BigDecimal> weights, Map<String, BigDecimal> thresholds) {
        if (weights == null || !weights.keySet().equals(WEIGHT_KEYS)) throw new ValidationException("weights must contain exactly skill, embedding, llm, project, preference and company");
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100")) != 0) throw new ValidationException("weights must total 100");
        if (weights.values().stream().anyMatch(value -> value == null || value.signum() < 0)) throw new ValidationException("weights must be non-negative");
        if (thresholds == null || !thresholds.keySet().equals(LEVEL_KEYS)) throw new ValidationException("levelThresholds must contain exactly S, A, B, C and D");
        BigDecimal s = thresholds.get("S"), a = thresholds.get("A"), b = thresholds.get("B"), c = thresholds.get("C"), d = thresholds.get("D");
        if (!(s.compareTo(a) > 0 && a.compareTo(b) > 0 && b.compareTo(c) > 0 && c.compareTo(d) > 0 && d.compareTo(BigDecimal.ZERO) == 0)) {
            throw new ValidationException("level thresholds must descend S > A > B > C > D and D must be 0");
        }
    }

    private void deactivateCurrent(Long userId) {
        MatchConfigEntity current = mapper.selectOne(new LambdaQueryWrapper<MatchConfigEntity>()
                .eq(MatchConfigEntity::getUserId, userId).eq(MatchConfigEntity::getActive, true).last("LIMIT 1"));
        if (current != null) { current.setActive(false); mapper.updateById(current); }
    }

    private MatchConfigEntity owned(Long userId, String publicId) {
        MatchConfigEntity entity = mapper.selectOne(new LambdaQueryWrapper<MatchConfigEntity>()
                .eq(MatchConfigEntity::getUserId, userId).eq(MatchConfigEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Match config");
        return entity;
    }

    private Map<String, BigDecimal> map(String value) {
        try { return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, BigDecimal>>() { }); }
        catch (Exception exception) { throw new IllegalStateException("Stored match config JSON is invalid", exception); }
    }

    private Map<String, BigDecimal> defaultWeights() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("skill", new BigDecimal("30")); values.put("embedding", new BigDecimal("20"));
        values.put("llm", new BigDecimal("25")); values.put("project", new BigDecimal("10"));
        values.put("preference", new BigDecimal("10")); values.put("company", new BigDecimal("5"));
        return values;
    }

    private Map<String, BigDecimal> defaultThresholds() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("S", new BigDecimal("90")); values.put("A", new BigDecimal("80"));
        values.put("B", new BigDecimal("70")); values.put("C", new BigDecimal("60")); values.put("D", BigDecimal.ZERO);
        return values;
    }
}

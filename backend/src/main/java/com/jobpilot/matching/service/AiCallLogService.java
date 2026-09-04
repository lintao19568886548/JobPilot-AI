package com.jobpilot.matching.service;

import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.matching.domain.AiCallLogEntity;
import com.jobpilot.matching.mapper.AiCallLogMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCallLogService {
    private final AiCallLogMapper mapper;
    public AiCallLogService(AiCallLogMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallLogEntity start(Long userId, String requestHash) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setUserId(userId); entity.setTaskType("MATCH_ANALYSIS"); entity.setProvider("OPENAI_COMPATIBLE");
        entity.setPromptVersion("matching/v1"); entity.setRequestHash(requestHash); entity.setCurrency("USD");
        entity.setStatus("SKIPPED_NOT_CONFIGURED"); entity.setTraceId(TraceContext.getTraceId()); entity.setStartedAt(LocalDateTime.now());
        mapper.insert(entity); return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallLogEntity start(Long userId, String taskType, Long promptTemplateId,
                                 String promptVersion, String promptHash, String requestHash) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setUserId(userId); entity.setTaskType(taskType); entity.setPromptTemplateId(promptTemplateId);
        entity.setProvider("RULES_ENGINE"); entity.setPromptVersion(promptVersion); entity.setPromptHash(promptHash);
        entity.setRequestHash(requestHash); entity.setCurrency("USD"); entity.setEstimatedCost(BigDecimal.ZERO);
        entity.setStatus("SKIPPED_NOT_CONFIGURED"); entity.setTraceId(TraceContext.getTraceId());
        entity.setStartedAt(LocalDateTime.now()); mapper.insert(entity); return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long id, String provider, String model, String responseHash, Integer inputTokens, Integer outputTokens,
                       Integer durationMs, String status, String errorCode) {
        AiCallLogEntity entity = mapper.selectById(id);
        if (entity == null) return;
        entity.setProvider(provider); entity.setModel(model); entity.setResponseHash(responseHash);
        entity.setInputTokens(inputTokens); entity.setOutputTokens(outputTokens); entity.setDurationMs(durationMs);
        entity.setEstimatedCost(BigDecimal.ZERO); entity.setStatus(status); entity.setErrorCode(errorCode);
        entity.setFinishedAt(LocalDateTime.now()); mapper.updateById(entity);
    }
}

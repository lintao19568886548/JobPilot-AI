package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("ai_call_logs")
public class AiCallLogEntity extends AuditEntity {
    private Long userId;
    private String taskType;
    private Long promptTemplateId;
    private String provider;
    private String model;
    private String promptVersion;
    private String promptHash;
    private String requestHash;
    private String responseHash;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal estimatedCost;
    private String currency;
    private Integer durationMs;
    private String status;
    private String errorCode;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

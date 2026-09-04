package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("outbox_events")
public class OutboxEventEntity extends BaseEntity {
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payloadJson;
    private String idempotencyKey;
    private String status;
    private LocalDateTime availableAt;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime publishedAt;
    private String lastErrorSafe;
}

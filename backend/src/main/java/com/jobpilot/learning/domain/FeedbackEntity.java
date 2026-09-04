package com.jobpilot.learning.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("feedback")
public class FeedbackEntity extends AuditEntity {
    private Long userId;
    private Long jobId;
    private Long jobMatchId;
    private Long applicationId;
    private String sourceType;
    private String sourcePublicId;
    private String eventType;
    private BigDecimal labelValue;
    private LocalDateTime occurredAt;
    private String featureSchemaVersion;
    private String featureSnapshotJson;
    private String featureHash;
    private String source;
}

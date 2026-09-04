package com.jobpilot.recommendation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recommendation_events")
public class RecommendationEventEntity extends AuditEntity {
    private Long userId;
    private Long recommendationId;
    private Long jobId;
    private Long jobMatchId;
    private String eventType;
    private String reason;
    private String previousStateJson;
    private String currentStateJson;
    private String featureSnapshotJson;
    private String traceId;
    private LocalDateTime occurredAt;
}

package com.jobpilot.recommendation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recommendation_refresh_runs")
public class RecommendationRefreshRunEntity extends BaseEntity {
    private Long userId;
    private String status;
    private String idempotencyKey;
    private String requestHash;
    private String criteriaJson;
    private Boolean forceRun;
    private Integer totalCount;
    private Integer submittedCount;
    private Integer reusedCount;
    private Integer succeededCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessageSafe;
}

package com.jobpilot.recommendation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recommendation_refresh_items")
public class RecommendationRefreshItemEntity extends BaseEntity {
    private Long refreshRunId;
    private Long userId;
    private Long jobId;
    private Long matchRunId;
    private String status;
    private Boolean reused;
    private String errorMessageSafe;
}

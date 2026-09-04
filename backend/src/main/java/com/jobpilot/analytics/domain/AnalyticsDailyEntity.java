package com.jobpilot.analytics.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("analytics_daily")
public class AnalyticsDailyEntity extends BaseEntity {
    private Long userId;
    private LocalDate metricDate;
    private String dimensionType;
    private String dimensionKey;
    private Integer jobCount;
    private Integer evaluatedCount;
    private Integer highMatchCount;
    private Integer favoriteCount;
    private Integer ignoredCount;
    private Integer queuedCount;
    private Integer appliedCount;
    private Integer viewedCount;
    private Integer repliedCount;
    private Integer writtenTestCount;
    private Integer interviewStageCount;
    private Integer offerStageCount;
    private Integer terminalCount;
}

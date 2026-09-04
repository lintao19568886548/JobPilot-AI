package com.jobpilot.learning.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("feedback_rebuild_runs")
public class FeedbackRebuildRunEntity extends BaseEntity {
    private Long userId;
    private String idempotencyKey;
    private String requestHash;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer createdCount;
    private Integer reusedCount;
    private Integer skippedNoHistoricalMatch;
    private Boolean leakageSafe;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

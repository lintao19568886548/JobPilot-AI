package com.jobpilot.learning.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("ltr_training_runs")
public class LtrTrainingRunEntity extends BaseEntity {
    private Long userId;
    private String idempotencyKey;
    private String requestHash;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private LocalDateTime cutoffAt;
    private Integer minimumSample;
    private Integer sampleCount;
    private Integer trainCount;
    private Integer validationCount;
    private String status;
    private String featureSchemaVersion;
    private Boolean leakageSafe;
    private String metricsJson;
    private String parametersJson;
    private String errorMessageSafe;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

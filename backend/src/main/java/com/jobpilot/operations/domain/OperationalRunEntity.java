package com.jobpilot.operations.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("operational_runs")
public class OperationalRunEntity extends BaseEntity {
    private Long userId;
    private String scope;
    private String runType;
    private String status;
    private String batchId;
    private String idempotencyKey;
    private String requestHash;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String metricsJson;
    private String summarySafe;
    private String artifactManifestPath;
    private String artifactSha256;
    private Long rpoSeconds;
    private Long rtoSeconds;
    private String errorCode;
    private String traceId;
    private String createdBy;
}

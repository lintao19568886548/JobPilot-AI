package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("applications")
public class ApplicationEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long jobSourceId;
    private Long resumeVersionId;
    private Long recruiterId;
    private Long queueItemId;
    private String status;
    private String applicationMode;
    private LocalDateTime appliedAt;
    private LocalDateTime lastStatusAt;
    private String externalApplicationId;
    private String sourceUrlSnapshot;
    private String notes;
    private String idempotencyKey;
    private String requestHash;
}

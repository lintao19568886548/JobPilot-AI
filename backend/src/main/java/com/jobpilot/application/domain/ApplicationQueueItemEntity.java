package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("application_queue_items")
public class ApplicationQueueItemEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long jobMatchId;
    private Long resumeVersionId;
    private String greetingReference;
    private String mode;
    private String status;
    private Integer priority;
    private LocalDateTime scheduledAt;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime preparedAt;
    private String idempotencyKey;
    private String requestHash;
    private String lastErrorCode;
    private String lastErrorMessage;
}

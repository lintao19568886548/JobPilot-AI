package com.jobpilot.automation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("automation_tasks")
public class AutomationTaskEntity extends BaseEntity {
    private Long userId;
    private Long extensionDeviceId;
    private Long queueItemId;
    private Long platformPolicyId;
    private String idempotencyKey;
    private String requestHash;
    private String taskTokenHash;
    private LocalDateTime expiresAt;
    private String targetUrl;
    private String taskType;
    private String status;
    private Boolean externallySubmitted;
    private Boolean applicationCreated;
    private Boolean finalConfirmationRequired;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

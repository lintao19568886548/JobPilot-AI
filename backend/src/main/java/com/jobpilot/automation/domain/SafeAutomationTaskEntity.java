package com.jobpilot.automation.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("safe_automation_tasks")
public class SafeAutomationTaskEntity extends BaseEntity {private Long userId;private Long automationRuleId;private String taskType;private String idempotencyKey;private String requestHash;private String status;private String inputJson;private String outputJson;private Integer attemptCount;private Integer maxAttempts;private LocalDateTime scheduledAt;private LocalDateTime nextRetryAt;private LocalDateTime startedAt;private LocalDateTime finishedAt;private String errorCode;private String errorMessageSafe;private String traceId;private Boolean externalMessageSent;private Boolean externalSubmission;private Boolean externalMutation;private Boolean userConfirmationRequired;}

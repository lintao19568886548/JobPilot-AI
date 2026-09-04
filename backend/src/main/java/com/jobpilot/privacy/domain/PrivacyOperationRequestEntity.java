package com.jobpilot.privacy.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("privacy_operation_requests")
public class PrivacyOperationRequestEntity extends BaseEntity {
    private Long userId;
    private String operationType;
    private String status;
    private String requestHash;
    private String idempotencyKey;
    private String scopeCountsJson;
    private LocalDateTime executedAt;
}

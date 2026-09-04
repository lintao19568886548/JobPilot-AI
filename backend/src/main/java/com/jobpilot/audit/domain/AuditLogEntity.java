package com.jobpilot.audit.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("audit_logs")
public class AuditLogEntity extends AuditEntity {

    private Long userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String traceId;
}


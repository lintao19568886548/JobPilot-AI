package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("application_logs")
public class ApplicationLogEntity extends AuditEntity {
    private Long applicationId;
    private Long userId;
    private String fromStatus;
    private String toStatus;
    private String eventType;
    private LocalDateTime occurredAt;
    private String source;
    private Long actorUserId;
    private String note;
    private String evidenceJson;
    private String traceId;
}

package com.jobpilot.automation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("automation_task_steps")
public class AutomationTaskStepEntity extends AuditEntity {
    private Long taskId;
    private Integer sequenceNo;
    private String stepType;
    private String status;
    private String selectorHint;
    private String detailJson;
    private LocalDateTime occurredAt;
    private String traceId;
}

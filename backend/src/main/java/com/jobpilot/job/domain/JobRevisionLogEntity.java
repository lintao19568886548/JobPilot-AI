package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_revision_logs")
public class JobRevisionLogEntity extends AuditEntity {
    private Long userId;
    private Long jobId;
    private String beforeJson;
    private String afterJson;
    private String changedFieldsJson;
    private String reason;
    private String traceId;
}

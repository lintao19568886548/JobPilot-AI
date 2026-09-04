package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_import_tasks")
public class JobImportTaskEntity extends BaseEntity {
    private Long userId;
    private String importType;
    private String sourceUrl;
    private String fileName;
    private String fileHash;
    private String idempotencyKey;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private String errorSummaryJson;
}

package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_import_errors")
public class JobImportErrorEntity extends AuditEntity {
    private Long importTaskId;
    @TableField("source_row_number")
    private Integer rowNumber;
    private String errorCode;
    private String errorMessage;
    private String fieldName;
    private String rawPreview;
}

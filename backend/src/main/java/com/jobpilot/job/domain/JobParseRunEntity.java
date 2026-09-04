package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_parse_runs")
public class JobParseRunEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private String status;
    private String parserMode;
    private String parserVersion;
    private String schemaVersion;
    private String promptVersion;
    private String modelName;
    private String inputHash;
    private String outputHash;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Integer elapsedMs;
}

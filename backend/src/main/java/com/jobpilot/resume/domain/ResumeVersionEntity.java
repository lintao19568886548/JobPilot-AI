package com.jobpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_versions")
public class ResumeVersionEntity extends BaseEntity {

    private Long resumeId;
    private Long parentVersionId;
    private Integer versionNumber;
    private String versionName;
    private String contentJson;
    private String renderedText;
    private String sourceType;
    private String createdBy;
    private Boolean active;
    private String contentHash;
    private Long tailoredForJobId;
    private Long promptTemplateId;
    private Long aiCallId;
    private String modelName;
    private String truthCheckStatus;
}

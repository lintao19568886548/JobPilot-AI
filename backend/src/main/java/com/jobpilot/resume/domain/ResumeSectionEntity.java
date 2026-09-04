package com.jobpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_sections")
public class ResumeSectionEntity extends BaseEntity {

    private Long resumeVersionId;
    private String sectionType;
    private String contentJson;
    private Integer sortOrder;
}


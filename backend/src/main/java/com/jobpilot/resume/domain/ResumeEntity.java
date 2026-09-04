package com.jobpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resumes")
public class ResumeEntity extends BaseEntity {

    private Long userId;
    private String name;
    private String targetRole;
    private Boolean master;
    private Boolean defaultResume;
    private String description;
    private String status;
    private Long currentVersionId;
}


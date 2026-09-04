package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("skills")
public class SkillEntity extends BaseEntity {

    private String canonicalName;
    private String displayName;
    private String category;
    private String description;
    private String status;
}


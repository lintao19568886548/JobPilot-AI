package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("projects")
public class ProjectEntity extends BaseEntity {

    private Long userId;
    private String name;
    private String role;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
    private String background;
    private String responsibilities;
    private String achievements;
    private String technologiesJson;
    private String repoUrl;
    private String demoUrl;
    private Boolean featured;
    private Integer sortOrder;
}


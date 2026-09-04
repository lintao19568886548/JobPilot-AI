package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("educations")
public class EducationEntity extends BaseEntity {

    private Long userId;
    private String school;
    private String degree;
    private String major;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer graduationYear;
    private String description;
    private Integer sortOrder;
}


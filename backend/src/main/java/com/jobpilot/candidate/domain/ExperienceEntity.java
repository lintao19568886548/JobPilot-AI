package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("experiences")
public class ExperienceEntity extends BaseEntity {

    private Long userId;
    private String companyName;
    private String role;
    private String employmentType;
    private String location;
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private Boolean currentlyWorking;
    private String description;
    private String responsibilities;
    private String achievements;
    private String technologiesJson;
    private Integer sortOrder;
}

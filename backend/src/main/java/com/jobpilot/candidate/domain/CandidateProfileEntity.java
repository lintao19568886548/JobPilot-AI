package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("candidate_profiles")
public class CandidateProfileEntity extends BaseEntity {

    private Long userId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fullName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String headline;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentCity;
    private String targetCitiesJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer graduationYear;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String highestEducation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String school;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String major;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal yearsOfExperience;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String jobStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String githubUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String personalWebsite;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String summary;
    private String targetRolesJson;
    private String targetIndustriesJson;
    private String targetCompanyTypesJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal targetSalaryMin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal targetSalaryMax;
    private String salaryCurrency;
    private Boolean acceptRemote;
    private Boolean acceptRelocation;
    private Integer profileCompleteness;
}

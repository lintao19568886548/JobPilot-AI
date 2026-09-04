package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("companies")
public class CompanyEntity extends BaseEntity {
    private String normalizedName;
    private String displayName;
    private String website;
    private String industry;
    private String companySize;
    private String financingStage;
    private String headquartersCity;
    private String description;
    private String verifiedSource;
    private String riskFlagsJson;
}

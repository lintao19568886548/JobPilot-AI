package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("jobs")
public class JobEntity extends BaseEntity {
    private Long userId;
    private Long companyId;
    private String title;
    private String normalizedTitle;
    private String canonicalJobKey;
    private String fingerprintHash;
    private String city;
    private String district;
    private String workplaceText;
    private String remoteType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Integer salaryMonths;
    private String currency;
    private String salaryText;
    private String education;
    private BigDecimal experienceMinYears;
    private BigDecimal experienceMaxYears;
    private Integer graduateYear;
    private String jobType;
    private String descriptionRaw;
    private String descriptionClean;
    private String responsibilitiesJson;
    private String requirementsJson;
    private String businessDomain;
    private String teamName;
    private LocalDateTime publishAt;
    private LocalDateTime firstCollectedAt;
    private LocalDateTime lastCollectedAt;
    private LocalDateTime closedAt;
    private String status;
    private String parseStatus;
    private String parserVersion;
    private String parserMode;
    private String rawContentHash;
    private String parseResultJson;
    private String manualFieldsJson;
}

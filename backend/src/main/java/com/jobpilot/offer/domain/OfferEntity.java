package com.jobpilot.offer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("offers")
public class OfferEntity extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private Long jobId;
    private Long companyId;
    private Long resumeVersionId;
    private String companyNameSnapshot;
    private String roleSnapshot;
    private String status;
    private BigDecimal baseSalary;
    private String salaryPeriod;
    private BigDecimal salaryMonths;
    private String currency;
    private BigDecimal guaranteedBonus;
    private BigDecimal variableBonusMin;
    private BigDecimal variableBonusMax;
    private String equityText;
    private String housingText;
    private String workTimeText;
    private String overtimeText;
    private String location;
    private String remoteType;
    private Integer probationMonths;
    private BigDecimal probationSalaryRatio;
    private LocalDate startDate;
    private LocalDateTime deadlineAt;
    private String timezone;
    private String notes;
}

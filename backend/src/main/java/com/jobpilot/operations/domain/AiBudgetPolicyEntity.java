package com.jobpilot.operations.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_budget_policies")
public class AiBudgetPolicyEntity extends BaseEntity {
    private Long userId;
    private Long dailyTokenLimit;
    private Long monthlyTokenLimit;
    private BigDecimal dailyCostLimit;
    private BigDecimal monthlyCostLimit;
    private String currency;
    private BigDecimal warningThresholdPercent;
    private Boolean enabled;
}

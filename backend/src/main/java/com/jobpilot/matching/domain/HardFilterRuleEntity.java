package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("hard_filter_rules")
public class HardFilterRuleEntity extends BaseEntity {
    private Long userId;
    private String ruleKey;
    private String name;
    private String ruleType;
    @TableField("rule_operator")
    private String operator;
    private String operandJson;
    private String resultAction;
    private BigDecimal penalty;
    private Integer priority;
    private Boolean active;
    private Integer versionNo;
}

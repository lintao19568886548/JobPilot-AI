package com.jobpilot.automation.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("automation_rules")
public class AutomationRuleEntity extends BaseEntity {private Long userId;private String name;private String ruleType;private String scheduleExpression;private String timezone;private String configJson;private Boolean enabled;private String status;private Integer maxAttempts;private LocalDateTime lastRunAt;private LocalDateTime nextRunAt;}

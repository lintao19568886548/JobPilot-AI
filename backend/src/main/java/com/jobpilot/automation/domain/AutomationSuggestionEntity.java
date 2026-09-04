package com.jobpilot.automation.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("automation_suggestions")
public class AutomationSuggestionEntity extends BaseEntity {private Long userId;private Long automationRuleId;private Long taskId;private String dedupKey;private String suggestionType;private String resourceType;private String resourceId;private String title;private String body;private String evidenceJson;private String status;private LocalDateTime decidedAt;}

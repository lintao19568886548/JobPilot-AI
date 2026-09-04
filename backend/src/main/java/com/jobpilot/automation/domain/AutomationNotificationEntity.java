package com.jobpilot.automation.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("automation_notifications")
public class AutomationNotificationEntity extends BaseEntity {private Long userId;private Long taskId;private String dedupKey;private String notificationType;private String title;private String body;private String resourceType;private String resourceId;private String status;private LocalDateTime readAt;}

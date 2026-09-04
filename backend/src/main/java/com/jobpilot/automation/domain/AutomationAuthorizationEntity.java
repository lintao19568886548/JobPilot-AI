package com.jobpilot.automation.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("automation_authorizations")
public class AutomationAuthorizationEntity extends BaseEntity {private Long userId;private String platform;private String scopesJson;private String scopeHash;private String evidenceType;private String evidenceRef;private LocalDateTime grantedAt;private LocalDateTime expiresAt;private LocalDateTime revokedAt;private String status;}

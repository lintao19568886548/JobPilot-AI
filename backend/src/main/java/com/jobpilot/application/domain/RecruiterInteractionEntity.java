package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recruiter_interactions")
public class RecruiterInteractionEntity extends AuditEntity {
    private Long userId;
    private Long recruiterId;
    private Long applicationId;
    private String channel;
    private String direction;
    private LocalDateTime occurredAt;
    private String summary;
    private LocalDateTime followUpAt;
}

package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("communication_drafts")
public class CommunicationDraftEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long applicationId;
    private Long resumeVersionId;
    private Long recruiterId;
    private String channel;
    private String purpose;
    private String content;
    private Integer charCount;
    private String evidenceRefsJson;
    private Long promptTemplateId;
    private Long aiCallId;
    private String executionMode;
    private String truthCheckStatus;
    private String status;
    private String idempotencyKey;
    private String requestHash;
    private LocalDateTime approvedAt;
    private LocalDateTime usedAt;
}

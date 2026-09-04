package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("resume_tailor_runs")
public class ResumeTailorRunEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long baseResumeVersionId;
    private Long targetResumeId;
    private Long promptTemplateId;
    private Long aiCallId;
    private Long approvedResumeVersionId;
    private String idempotencyKey;
    private String requestHash;
    private String inputHash;
    private String strategy;
    private String status;
    private String executionMode;
    private String promptVersion;
    private String modelName;
    private String truthCheckStatus;
    private String evidenceSnapshotHash;
    private String proposedContentJson;
    private String proposedRenderedText;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime approvedAt;
}

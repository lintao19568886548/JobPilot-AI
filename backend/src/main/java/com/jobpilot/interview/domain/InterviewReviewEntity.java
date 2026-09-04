package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_reviews")
public class InterviewReviewEntity extends BaseEntity {
    private Long userId;
    private Long interviewId;
    private Integer reviewVersion;
    private String status;
    private String summaryText;
    private String sourceType;
    private String idempotencyKey;
    private String requestHash;
    private String promptVersion;
    private String modelName;
    private Long aiCallId;
    private String evidenceRefsJson;
    private LocalDateTime generatedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}

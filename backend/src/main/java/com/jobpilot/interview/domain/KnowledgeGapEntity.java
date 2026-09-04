package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("knowledge_gaps")
public class KnowledgeGapEntity extends BaseEntity {
    private Long userId;
    private Long sourceInterviewId;
    private Long sourceReviewId;
    private Long sourceReviewItemId;
    private String title;
    private String category;
    private String descriptionText;
    private String severity;
    private String evidenceJson;
    private String recommendedActionsJson;
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime dismissedAt;
}

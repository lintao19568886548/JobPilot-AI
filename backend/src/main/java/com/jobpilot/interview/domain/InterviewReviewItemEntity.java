package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_review_items")
public class InterviewReviewItemEntity extends BaseEntity {
    private Long userId;
    private Long reviewId;
    private String itemType;
    private String title;
    private String descriptionText;
    private String severity;
    private String evidenceText;
    private String evidenceRefsJson;
    private String recommendedActionsJson;
    private Integer displayOrder;
}

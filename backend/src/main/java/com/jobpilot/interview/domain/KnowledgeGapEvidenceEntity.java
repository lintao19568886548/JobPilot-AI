package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("knowledge_gap_evidence")
public class KnowledgeGapEvidenceEntity extends BaseEntity {
    private Long userId;
    private Long knowledgeGapId;
    private Long interviewId;
    private Long reviewId;
    private Long questionId;
    private String evidenceType;
    private String evidenceText;
}

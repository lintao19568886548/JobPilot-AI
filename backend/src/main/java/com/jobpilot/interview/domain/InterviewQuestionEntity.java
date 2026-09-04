package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_questions")
public class InterviewQuestionEntity extends BaseEntity {
    private Long userId;
    private Long interviewId;
    private Long roundId;
    private String sourceType;
    private String category;
    private String difficulty;
    private String questionText;
    private String purposeText;
    private String basisText;
    private String answerFramework;
    private String suggestedFollowUpsJson;
    private String riskNotes;
    private String evidenceRefsJson;
    private String predictionKey;
    private String requestHash;
    private String promptVersion;
    private String modelName;
    private Long aiCallId;
    private LocalDateTime generatedAt;
    private Integer displayOrder;
}

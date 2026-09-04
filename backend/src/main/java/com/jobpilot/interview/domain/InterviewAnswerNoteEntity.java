package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_answer_notes")
public class InterviewAnswerNoteEntity extends BaseEntity {
    private Long userId;
    private Long interviewId;
    private Long questionId;
    private Integer noteVersion;
    private String answerText;
    private Integer selfRating;
    private LocalDateTime userRecordedAt;
}

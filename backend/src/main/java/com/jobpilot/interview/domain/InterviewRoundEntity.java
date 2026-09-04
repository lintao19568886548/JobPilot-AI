package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_rounds")
public class InterviewRoundEntity extends BaseEntity {
    private Long userId;
    private Long interviewId;
    private Integer roundNo;
    private String roundType;
    private String title;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private String timezone;
    private String format;
    private String meetingLink;
    private String location;
    private String interviewerName;
    private String status;
    private String result;
    private String notes;
    private LocalDateTime completedAt;
}

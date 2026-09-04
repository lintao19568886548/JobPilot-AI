package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interview_reminders")
public class InterviewReminderEntity extends BaseEntity {
    private Long userId;
    private Long interviewId;
    private Long roundId;
    private String reminderType;
    private String title;
    private LocalDateTime remindAt;
    private String timezone;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
}

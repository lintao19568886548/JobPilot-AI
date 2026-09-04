package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("resume_version_metrics")
public class ResumeVersionMetricEntity extends BaseEntity {
    private Long userId;
    private Long resumeVersionId;
    private Integer queueUseCount;
    private Integer applicationCount;
    private Integer replyCount;
    private Integer interviewCount;
    private Integer offerCount;
    private LocalDateTime calculatedAt;
}

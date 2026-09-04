package com.jobpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("interviews")
public class InterviewEntity extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private Long jobId;
    private Long companyId;
    private Long resumeVersionId;
    private String companyNameSnapshot;
    private String roleSnapshot;
    private String status;
    private String result;
    private String timezone;
    private String notes;
}

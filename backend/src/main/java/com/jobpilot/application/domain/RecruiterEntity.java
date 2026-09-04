package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recruiters")
public class RecruiterEntity extends BaseEntity {
    private Long userId;
    private Long companyId;
    private String name;
    private String position;
    private String platform;
    private String platformRecruiterId;
    private String contactMasked;
    private String email;
    private String phone;
    private LocalDateTime lastContactAt;
    private LocalDateTime nextFollowUpAt;
    private String communicationStatus;
    private String notes;
}

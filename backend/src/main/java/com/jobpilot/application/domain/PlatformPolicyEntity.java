package com.jobpilot.application.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("platform_policies")
public class PlatformPolicyEntity extends BaseEntity {
    private String platform;
    private String collectionMode;
    private String applicationMode;
    private Boolean requiresFinalConfirmation;
    private String rateLimitJson;
    private String policySourceUrl;
    private LocalDateTime reviewedAt;
    private String status;
}

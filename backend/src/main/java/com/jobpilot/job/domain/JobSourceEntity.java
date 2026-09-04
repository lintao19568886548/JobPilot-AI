package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_sources")
public class JobSourceEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private String platform;
    private String platformJobId;
    private String sourceType;
    private String jobUrl;
    private String normalizedUrlHash;
    private String sourceTitle;
    private String sourceCompanyName;
    private String rawSnapshotJson;
    private LocalDateTime publishAt;
    private LocalDateTime collectedAt;
    private LocalDateTime lastSeenAt;
    private String availabilityStatus;
    private String collectorVersion;
    private Boolean userInitiated;
}

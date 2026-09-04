package com.jobpilot.analytics.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("analytics_snapshots")
public class AnalyticsSnapshotEntity extends BaseEntity {
    private Long userId;
    private String schemaVersion;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String inputHash;
    private String idempotencyKey;
    private String resultJson;
    private Integer rowCount;
    private LocalDateTime generatedAt;
}

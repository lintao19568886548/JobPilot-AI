package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("match_runs")
public class MatchRunEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long candidateProfileId;
    private Long resumeVersionId;
    private Long matchConfigId;
    private String idempotencyKey;
    private String inputHash;
    private Boolean forceRun;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Long resultMatchId;
    private String errorCode;
    private String errorMessageSafe;
    private LocalDateTime availableAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String traceId;
}

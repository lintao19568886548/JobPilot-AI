package com.jobpilot.recommendation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("job_recommendations")
public class JobRecommendationEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long latestMatchId;
    private String recommendationStatus;
    private Boolean favorite;
    private BigDecimal rankScore;
    private String rankVersion;
    private String rankBasisJson;
    private String ignoredReason;
    private LocalDateTime ignoredAt;
    private LocalDateTime lastEvaluatedAt;
}

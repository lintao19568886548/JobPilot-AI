package com.jobpilot.learning.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("ltr_shadow_results")
public class LtrShadowResultEntity extends AuditEntity {
    private Long userId;
    private Long modelVersionId;
    private Long recommendationId;
    private Long jobId;
    private BigDecimal currentScore;
    private BigDecimal shadowScore;
    private BigDecimal scoreDelta;
    private Integer currentRankPosition;
    private Integer shadowRankPosition;
    private String featureSnapshotJson;
}

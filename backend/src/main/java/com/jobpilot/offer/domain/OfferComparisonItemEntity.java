package com.jobpilot.offer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("offer_comparison_items")
public class OfferComparisonItemEntity extends BaseEntity {
    private Long userId;
    private Long comparisonId;
    private Long offerId;
    private Integer rankNo;
    private BigDecimal guaranteedAnnualCash;
    private BigDecimal potentialAnnualCash;
    private BigDecimal overallScore;
    private String dimensionsJson;
    private String explanationText;
}

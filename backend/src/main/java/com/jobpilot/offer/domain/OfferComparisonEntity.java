package com.jobpilot.offer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("offer_comparisons")
public class OfferComparisonEntity extends BaseEntity {
    private Long userId;
    private Integer comparisonVersion;
    private String name;
    private String offerIdsJson;
    private String weightsJson;
    private String inputSnapshotJson;
    private String inputHash;
    private String idempotencyKey;
    private String currencyGroup;
    private Boolean cashComparable;
    private String summaryText;
}

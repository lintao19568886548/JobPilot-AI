package com.jobpilot.offer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("offer_benefits")
public class OfferBenefitEntity extends BaseEntity {
    private Long userId;
    private Long offerId;
    private String benefitType;
    private String name;
    private String valueText;
    private BigDecimal quantifiedValue;
    private String currency;
    private Integer displayOrder;
}

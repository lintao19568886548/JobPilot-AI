package com.jobpilot.offer.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("offer_deadlines")
public class OfferDeadlineEntity extends BaseEntity {
    private Long userId;
    private Long offerId;
    private String deadlineType;
    private String title;
    private LocalDateTime dueAt;
    private String timezone;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
}

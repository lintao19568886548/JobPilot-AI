package com.jobpilot.offer.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.offer.domain.OfferDeadlineEntity;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferDeadlineMapper;
import com.jobpilot.offer.mapper.OfferMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OfferOwnershipRepository {
    private final OfferMapper offers;
    private final OfferDeadlineMapper deadlines;
    public OfferOwnershipRepository(OfferMapper offers, OfferDeadlineMapper deadlines) { this.offers = offers; this.deadlines = deadlines; }

    public OfferEntity offer(Long userId, String publicId) {
        OfferEntity value = offers.selectOne(new LambdaQueryWrapper<OfferEntity>()
                .eq(OfferEntity::getUserId, userId).eq(OfferEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Offer");
        return value;
    }

    public OfferEntity offerById(Long userId, Long id) {
        OfferEntity value = offers.selectOne(new LambdaQueryWrapper<OfferEntity>()
                .eq(OfferEntity::getUserId, userId).eq(OfferEntity::getId, id).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Offer");
        return value;
    }

    public OfferDeadlineEntity deadline(Long userId, String publicId) {
        OfferDeadlineEntity value = deadlines.selectOne(new LambdaQueryWrapper<OfferDeadlineEntity>()
                .eq(OfferDeadlineEntity::getUserId, userId).eq(OfferDeadlineEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Offer Deadline");
        offerById(userId, value.getOfferId());
        return value;
    }
}

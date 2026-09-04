package com.jobpilot.offer.service;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.offer.domain.OfferDeadlineEntity;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferDeadlineMapper;
import com.jobpilot.offer.repository.OfferOwnershipRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferDeadlineService {
    private static final Set<String> STATUSES = Set.of("PENDING", "DONE", "CANCELLED");
    private final OfferDeadlineMapper deadlines; private final OfferOwnershipRepository ownership;
    private final OfferViewAssembler views; private final AuditService audit;
    public OfferDeadlineService(OfferDeadlineMapper deadlines, OfferOwnershipRepository ownership,
                                OfferViewAssembler views, AuditService audit) {
        this.deadlines = deadlines; this.ownership = ownership; this.views = views; this.audit = audit;
    }

    public List<DeadlineView> list(Long userId, String status) {
        LambdaQueryWrapper<OfferDeadlineEntity> query = new LambdaQueryWrapper<OfferDeadlineEntity>()
                .eq(OfferDeadlineEntity::getUserId, userId)
                .apply("EXISTS (SELECT 1 FROM offers o WHERE o.id=offer_deadlines.offer_id AND o.deleted_at IS NULL)")
                .orderByAsc(OfferDeadlineEntity::getDueAt);
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!STATUSES.contains(normalized)) throw new com.jobpilot.common.exception.ValidationException("Unsupported deadline status");
            query.eq(OfferDeadlineEntity::getStatus, normalized);
        }
        return deadlines.selectList(query).stream().map(v -> views.deadline(v, ownership.offerById(userId, v.getOfferId()))).toList();
    }

    @Transactional
    public DeadlineView create(Long userId, DeadlineRequest request) {
        OfferEntity offer = ownership.offer(userId, request.offerId());
        if (OfferPolicy.terminal(offer.getStatus())) throw new BusinessException(4099004, "Cannot add a deadline to a terminal Offer", HttpStatus.CONFLICT);
        OfferDeadlineEntity entity = new OfferDeadlineEntity(); entity.setUserId(userId); entity.setOfferId(offer.getId());
        apply(entity, request.deadlineType(), request.title(), request.dueAt(), request.timezone()); entity.setStatus("PENDING");
        try { deadlines.insert(entity); } catch (DataIntegrityViolationException exception) { duplicate(); }
        audit.record(userId, "OFFER_DEADLINE_CREATE", "OFFER_DEADLINE", entity.getPublicId());
        return views.deadline(entity, offer);
    }

    @Transactional
    public DeadlineView update(Long userId, String publicId, DeadlineUpdateRequest request) {
        OfferDeadlineEntity entity = ownership.deadline(userId, publicId); OfferService.version(entity.getVersion(), request.version());
        if (!"PENDING".equals(entity.getStatus())) throw new BusinessException(4099005, "Only pending deadlines can be edited", HttpStatus.CONFLICT);
        apply(entity, request.deadlineType(), request.title(), request.dueAt(), request.timezone());
        try { if (deadlines.updateById(entity) != 1) conflict(); } catch (DataIntegrityViolationException exception) { duplicate(); }
        audit.record(userId, "OFFER_DEADLINE_UPDATE", "OFFER_DEADLINE", publicId);
        return views.deadline(ownership.deadline(userId, publicId), ownership.offerById(userId, entity.getOfferId()));
    }

    @Transactional
    public DeadlineView change(Long userId, String publicId, String status, VersionRequest request) {
        OfferDeadlineEntity entity = ownership.deadline(userId, publicId); OfferService.version(entity.getVersion(), request.version());
        if (!"PENDING".equals(entity.getStatus())) throw new BusinessException(4099005, "Deadline is already final", HttpStatus.CONFLICT);
        entity.setStatus(status); LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if ("DONE".equals(status)) entity.setCompletedAt(now); else entity.setCancelledAt(now);
        if (deadlines.updateById(entity) != 1) conflict();
        audit.record(userId, "OFFER_DEADLINE_" + status, "OFFER_DEADLINE", publicId);
        return views.deadline(ownership.deadline(userId, publicId), ownership.offerById(userId, entity.getOfferId()));
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        OfferDeadlineEntity entity = ownership.deadline(userId, publicId); deadlines.deleteById(entity);
        audit.record(userId, "OFFER_DEADLINE_DELETE", "OFFER_DEADLINE", publicId);
    }

    private static void apply(OfferDeadlineEntity entity, String type, String title, java.time.OffsetDateTime dueAt, String timezone) {
        LocalDateTime due = OfferTime.toUtc(dueAt, timezone); OfferTime.validateFuture(due);
        entity.setDeadlineType(type); entity.setTitle(title.trim()); entity.setDueAt(due); entity.setTimezone(timezone);
    }
    private static void duplicate() { throw new BusinessException(4099006, "Duplicate active Offer deadline", HttpStatus.CONFLICT); }
    private static void conflict() { throw new BusinessException(4099003, "Resource version conflict", HttpStatus.CONFLICT); }
}

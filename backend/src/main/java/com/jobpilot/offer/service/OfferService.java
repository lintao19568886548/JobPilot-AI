package com.jobpilot.offer.service;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.offer.domain.OfferBenefitEntity;
import com.jobpilot.offer.domain.OfferDeadlineEntity;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferBenefitMapper;
import com.jobpilot.offer.mapper.OfferDeadlineMapper;
import com.jobpilot.offer.mapper.OfferMapper;
import com.jobpilot.offer.repository.OfferOwnershipRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "RECEIVED", "CONSIDERING", "ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN");
    private final OfferMapper offers;
    private final OfferBenefitMapper benefits;
    private final OfferDeadlineMapper deadlines;
    private final OfferReferenceService references;
    private final OfferOwnershipRepository ownership;
    private final OfferViewAssembler views;
    private final AuditService audit;

    public OfferService(OfferMapper offers, OfferBenefitMapper benefits, OfferDeadlineMapper deadlines,
                        OfferReferenceService references, OfferOwnershipRepository ownership,
                        OfferViewAssembler views, AuditService audit) {
        this.offers = offers; this.benefits = benefits; this.deadlines = deadlines; this.references = references;
        this.ownership = ownership; this.views = views; this.audit = audit;
    }

    public OfferPage list(Long userId, int page, int size, String status, String company, String role,
                          String currency, LocalDateTime from, LocalDateTime to, String applicationId) {
        if (page < 1 || size < 1 || size > 100) throw new ValidationException("page must be >= 1 and size must be 1..100");
        LambdaQueryWrapper<OfferEntity> query = new LambdaQueryWrapper<OfferEntity>()
                .eq(OfferEntity::getUserId, userId).orderByDesc(OfferEntity::getUpdatedAt).orderByDesc(OfferEntity::getId);
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!STATUSES.contains(normalized)) throw new ValidationException("Unsupported Offer status");
            query.eq(OfferEntity::getStatus, normalized);
        }
        if (company != null && !company.isBlank()) query.like(OfferEntity::getCompanyNameSnapshot, company.trim());
        if (role != null && !role.isBlank()) query.like(OfferEntity::getRoleSnapshot, role.trim());
        if (currency != null && !currency.isBlank()) query.eq(OfferEntity::getCurrency, currency.trim().toUpperCase());
        if (from != null) query.ge(OfferEntity::getDeadlineAt, from);
        if (to != null) query.le(OfferEntity::getDeadlineAt, to);
        if (applicationId != null && !applicationId.isBlank()) query.eq(OfferEntity::getApplicationId, references.application(userId, applicationId).application().getId());
        Page<OfferEntity> result = offers.selectPage(Page.of(page, size), query);
        return new OfferPage(result.getRecords().stream().map(views::offer).toList(), result.getTotal(), page, size);
    }

    @Transactional
    public OfferView create(Long userId, OfferCreateRequest request) {
        OfferReferenceService.References linked = references.application(userId, request.applicationId());
        OfferEntity entity = new OfferEntity();
        entity.setUserId(userId); entity.setApplicationId(linked.application().getId()); entity.setJobId(linked.job().getId());
        entity.setCompanyId(linked.company().getId()); entity.setResumeVersionId(linked.resumeVersion().getId());
        entity.setCompanyNameSnapshot(linked.company().getDisplayName()); entity.setRoleSnapshot(linked.job().getTitle());
        entity.setStatus("RECEIVED");
        apply(entity, request.baseSalary(), request.salaryPeriod(), request.salaryMonths(), request.currency(),
                request.guaranteedBonus(), request.variableBonusMin(), request.variableBonusMax(), request.equity(),
                request.housing(), request.workTime(), request.overtime(), request.location(), request.remoteType(),
                request.probationMonths(), request.probationSalaryRatio(), request.startDate(), request.deadlineAt(),
                request.timezone(), request.notes());
        try { offers.insert(entity); }
        catch (DataIntegrityViolationException exception) {
            throw new BusinessException(4099002, "An active Offer already exists for this application", HttpStatus.CONFLICT);
        }
        replaceBenefits(userId, entity, request.benefits());
        if (entity.getDeadlineAt() != null) createDecisionDeadline(userId, entity);
        audit.record(userId, "OFFER_CREATE", "OFFER", entity.getPublicId());
        return views.offer(entity);
    }

    public OfferView get(Long userId, String publicId) { return views.offer(ownership.offer(userId, publicId)); }

    @Transactional
    public OfferView update(Long userId, String publicId, OfferUpdateRequest request) {
        OfferEntity entity = ownership.offer(userId, publicId);
        version(entity.getVersion(), request.version());
        apply(entity, request.baseSalary(), request.salaryPeriod(), request.salaryMonths(), request.currency(),
                request.guaranteedBonus(), request.variableBonusMin(), request.variableBonusMax(), request.equity(),
                request.housing(), request.workTime(), request.overtime(), request.location(), request.remoteType(),
                request.probationMonths(), request.probationSalaryRatio(), request.startDate(), request.deadlineAt(),
                request.timezone(), request.notes());
        if (offers.updateById(entity) != 1) conflict();
        replaceBenefits(userId, entity, request.benefits());
        syncDecisionDeadline(userId, entity);
        audit.record(userId, "OFFER_UPDATE", "OFFER", publicId);
        return get(userId, publicId);
    }

    @Transactional
    public OfferView transition(Long userId, String publicId, OfferStatusRequest request) {
        OfferEntity entity = ownership.offer(userId, publicId);
        version(entity.getVersion(), request.version());
        String status = request.status().toUpperCase();
        OfferPolicy.transition(entity.getStatus(), status); entity.setStatus(status);
        if (offers.updateById(entity) != 1) conflict();
        if (OfferPolicy.terminal(status)) cancelPendingDeadlines(userId, entity.getId());
        audit.record(userId, "OFFER_STATUS_UPDATE", "OFFER", publicId);
        return get(userId, publicId);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        OfferEntity entity = ownership.offer(userId, publicId);
        cancelPendingDeadlines(userId, entity.getId());
        deadlines.delete(new LambdaQueryWrapper<OfferDeadlineEntity>().eq(OfferDeadlineEntity::getOfferId, entity.getId()));
        benefits.delete(new LambdaQueryWrapper<OfferBenefitEntity>().eq(OfferBenefitEntity::getOfferId, entity.getId()));
        offers.deleteById(entity);
        audit.record(userId, "OFFER_DELETE", "OFFER", publicId);
    }

    public OfferDashboardView dashboard(Long userId) {
        List<OfferEntity> values = offers.selectList(new LambdaQueryWrapper<OfferEntity>().eq(OfferEntity::getUserId, userId));
        List<DeadlineView> pending = deadlines.selectList(new LambdaQueryWrapper<OfferDeadlineEntity>()
                        .eq(OfferDeadlineEntity::getUserId, userId).eq(OfferDeadlineEntity::getStatus, "PENDING")
                        .apply("EXISTS (SELECT 1 FROM offers o WHERE o.id=offer_deadlines.offer_id AND o.deleted_at IS NULL)")
                        .orderByAsc(OfferDeadlineEntity::getDueAt).last("LIMIT 10"))
                .stream().map(item -> views.deadline(item, ownership.offerById(userId, item.getOfferId()))).toList();
        return new OfferDashboardView(values.stream().filter(v -> !OfferPolicy.terminal(v.getStatus())).count(),
                values.stream().filter(v -> "ACCEPTED".equals(v.getStatus())).count(), pending, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void apply(OfferEntity entity, java.math.BigDecimal baseSalary, String salaryPeriod,
                       java.math.BigDecimal salaryMonths, String currency, java.math.BigDecimal guaranteedBonus,
                       java.math.BigDecimal variableBonusMin, java.math.BigDecimal variableBonusMax, String equity,
                       String housing, String workTime, String overtime, String location, String remoteType,
                       Integer probationMonths, java.math.BigDecimal probationRatio, java.time.LocalDate startDate,
                       OffsetDateTime deadlineAt, String timezone, String notes) {
        OfferTime.validateZone(timezone);
        entity.setBaseSalary(baseSalary); entity.setSalaryPeriod(salaryPeriod); entity.setSalaryMonths(salaryMonths);
        entity.setCurrency(currency.toUpperCase()); entity.setGuaranteedBonus(guaranteedBonus);
        entity.setVariableBonusMin(variableBonusMin); entity.setVariableBonusMax(variableBonusMax);
        entity.setEquityText(trim(equity)); entity.setHousingText(trim(housing)); entity.setWorkTimeText(trim(workTime));
        entity.setOvertimeText(trim(overtime)); entity.setLocation(trim(location)); entity.setRemoteType(remoteType);
        entity.setProbationMonths(probationMonths); entity.setProbationSalaryRatio(probationRatio); entity.setStartDate(startDate);
        entity.setDeadlineAt(OfferTime.toUtc(deadlineAt, timezone));
        if (entity.getDeadlineAt() != null) OfferTime.validateFuture(entity.getDeadlineAt());
        entity.setTimezone(timezone); entity.setNotes(trim(notes));
        OfferMoney.validate(entity);
    }

    private void replaceBenefits(Long userId, OfferEntity offer, List<BenefitRequest> values) {
        benefits.delete(new LambdaQueryWrapper<OfferBenefitEntity>().eq(OfferBenefitEntity::getOfferId, offer.getId()));
        if (values == null) return;
        for (BenefitRequest value : values) {
            if (value.quantifiedValue() != null && value.currency() == null) throw new ValidationException("currency is required for quantified benefits");
            OfferBenefitEntity entity = new OfferBenefitEntity();
            entity.setUserId(userId); entity.setOfferId(offer.getId()); entity.setBenefitType(value.benefitType());
            entity.setName(value.name().trim()); entity.setValueText(trim(value.value())); entity.setQuantifiedValue(value.quantifiedValue());
            entity.setCurrency(value.currency()); entity.setDisplayOrder(value.displayOrder() == null ? 0 : value.displayOrder());
            benefits.insert(entity);
        }
    }

    private void createDecisionDeadline(Long userId, OfferEntity offer) {
        OfferDeadlineEntity deadline = new OfferDeadlineEntity(); deadline.setUserId(userId); deadline.setOfferId(offer.getId());
        deadline.setDeadlineType("DECISION"); deadline.setTitle("Offer decision deadline"); deadline.setDueAt(offer.getDeadlineAt());
        deadline.setTimezone(offer.getTimezone()); deadline.setStatus("PENDING"); deadlines.insert(deadline);
    }

    private void syncDecisionDeadline(Long userId, OfferEntity offer) {
        List<OfferDeadlineEntity> pending = deadlines.selectList(new LambdaQueryWrapper<OfferDeadlineEntity>()
                .eq(OfferDeadlineEntity::getUserId, userId).eq(OfferDeadlineEntity::getOfferId, offer.getId())
                .eq(OfferDeadlineEntity::getDeadlineType, "DECISION").eq(OfferDeadlineEntity::getStatus, "PENDING"));
        if (offer.getDeadlineAt() == null) {
            pending.forEach(this::cancelDeadline);
            return;
        }
        if (pending.isEmpty()) {
            createDecisionDeadline(userId, offer);
            return;
        }
        OfferDeadlineEntity current = pending.stream().filter(value -> offer.getDeadlineAt().equals(value.getDueAt()))
                .findFirst().orElse(pending.getFirst());
        pending.stream().filter(value -> !value.getId().equals(current.getId())).forEach(this::cancelDeadline);
        current.setDueAt(offer.getDeadlineAt()); current.setTimezone(offer.getTimezone());
        current.setTitle("Offer decision deadline"); deadlines.updateById(current);
    }

    private void cancelDeadline(OfferDeadlineEntity value) {
        value.setStatus("CANCELLED"); value.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC)); deadlines.updateById(value);
    }

    private void cancelPendingDeadlines(Long userId, Long offerId) {
        for (OfferDeadlineEntity value : deadlines.selectList(new LambdaQueryWrapper<OfferDeadlineEntity>()
                .eq(OfferDeadlineEntity::getUserId, userId).eq(OfferDeadlineEntity::getOfferId, offerId)
                .eq(OfferDeadlineEntity::getStatus, "PENDING"))) {
            cancelDeadline(value);
        }
    }

    static void version(Integer current, Integer requested) { if (!current.equals(requested)) conflict(); }
    private static void conflict() { throw new BusinessException(4099003, "Resource version conflict", HttpStatus.CONFLICT); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

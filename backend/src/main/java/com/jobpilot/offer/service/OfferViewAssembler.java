package com.jobpilot.offer.service;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.offer.domain.OfferBenefitEntity;
import com.jobpilot.offer.domain.OfferDeadlineEntity;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferBenefitMapper;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OfferViewAssembler {
    private final OfferBenefitMapper benefits;
    private final ApplicationMapper applications;
    private final ResumeVersionMapper resumeVersions;
    private final JobMapper jobs;

    public OfferViewAssembler(OfferBenefitMapper benefits, ApplicationMapper applications,
                              ResumeVersionMapper resumeVersions, JobMapper jobs) {
        this.benefits = benefits; this.applications = applications; this.resumeVersions = resumeVersions; this.jobs = jobs;
    }

    public OfferView offer(OfferEntity value) {
        ApplicationEntity application = applications.selectById(value.getApplicationId());
        ResumeVersionEntity resume = resumeVersions.selectById(value.getResumeVersionId());
        JobEntity job = jobs.selectById(value.getJobId());
        List<BenefitView> benefitViews = benefits.selectList(new LambdaQueryWrapper<OfferBenefitEntity>()
                        .eq(OfferBenefitEntity::getOfferId, value.getId())
                        .orderByAsc(OfferBenefitEntity::getDisplayOrder).orderByAsc(OfferBenefitEntity::getId))
                .stream().map(this::benefit).toList();
        return new OfferView(value.getPublicId(), application == null ? null : application.getPublicId(),
                job == null ? null : job.getPublicId(),
                resume == null ? null : resume.getPublicId(), value.getCompanyNameSnapshot(),
                value.getRoleSnapshot(), value.getStatus(), value.getBaseSalary(), value.getSalaryPeriod(),
                value.getSalaryMonths(), value.getCurrency(), value.getGuaranteedBonus(),
                value.getVariableBonusMin(), value.getVariableBonusMax(), OfferMoney.guaranteedAnnualCash(value),
                OfferMoney.potentialAnnualCash(value), OfferMoney.probationMonthlyCash(value), value.getEquityText(),
                value.getHousingText(), value.getWorkTimeText(), value.getOvertimeText(), value.getLocation(),
                value.getRemoteType(), value.getProbationMonths(), value.getProbationSalaryRatio(),
                value.getStartDate(), OfferTime.fromUtc(value.getDeadlineAt()), value.getTimezone(), value.getNotes(),
                benefitViews, value.getVersion(), utc(value.getCreatedAt()), utc(value.getUpdatedAt()));
    }

    public BenefitView benefit(OfferBenefitEntity value) {
        return new BenefitView(value.getPublicId(), value.getBenefitType(), value.getName(), value.getValueText(),
                value.getQuantifiedValue(), value.getCurrency(), value.getDisplayOrder(), value.getVersion());
    }

    public DeadlineView deadline(OfferDeadlineEntity value, OfferEntity offer) {
        return new DeadlineView(value.getPublicId(), offer.getPublicId(), offer.getCompanyNameSnapshot(),
                offer.getRoleSnapshot(), value.getDeadlineType(), value.getTitle(), OfferTime.fromUtc(value.getDueAt()),
                value.getTimezone(), value.getStatus(), OfferTime.fromUtc(value.getCompletedAt()),
                OfferTime.fromUtc(value.getCancelledAt()), value.getVersion());
    }

    private static java.time.OffsetDateTime utc(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}

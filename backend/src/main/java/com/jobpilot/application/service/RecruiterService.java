package com.jobpilot.application.service;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.RecruiterEntity;
import com.jobpilot.application.domain.RecruiterInteractionEntity;
import com.jobpilot.application.mapper.RecruiterInteractionMapper;
import com.jobpilot.application.mapper.RecruiterMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterService {
    private final RecruiterMapper mapper;
    private final RecruiterInteractionMapper interactionMapper;
    private final CompanyMapper companyMapper;
    private final ApplicationService applicationService;
    private final AuditService audit;

    public RecruiterService(RecruiterMapper mapper, RecruiterInteractionMapper interactionMapper,
                            CompanyMapper companyMapper, ApplicationService applicationService,
                            AuditService audit) {
        this.mapper = mapper;
        this.interactionMapper = interactionMapper;
        this.companyMapper = companyMapper;
        this.applicationService = applicationService;
        this.audit = audit;
    }

    public List<RecruiterView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<RecruiterEntity>()
                        .eq(RecruiterEntity::getUserId, userId)
                        .orderByAsc(RecruiterEntity::getNextFollowUpAt)
                        .orderByDesc(RecruiterEntity::getUpdatedAt))
                .stream().map(item -> view(userId, item, false)).toList();
    }

    @Transactional
    public RecruiterView create(Long userId, RecruiterRequest request) {
        CompanyEntity company = company(request.companyId());
        RecruiterEntity entity = new RecruiterEntity();
        entity.setUserId(userId);
        entity.setCompanyId(company == null ? null : company.getId());
        entity.setName(request.name().trim());
        entity.setPosition(trim(request.position()));
        entity.setPlatform(upper(request.platform()));
        entity.setPlatformRecruiterId(trim(request.platformRecruiterId()));
        entity.setContactMasked(trim(request.contactMasked()));
        entity.setEmail(trim(request.email()));
        entity.setPhone(trim(request.phone()));
        entity.setNextFollowUpAt(request.nextFollowUpAt());
        entity.setCommunicationStatus(request.communicationStatus() == null ? "NEW" : request.communicationStatus());
        entity.setNotes(trim(request.notes()));
        mapper.insert(entity);
        audit.record(userId, "RECRUITER_CREATE", "RECRUITER", entity.getPublicId());
        return view(userId, entity, true);
    }

    public RecruiterView get(Long userId, String publicId) {
        return view(userId, owned(userId, publicId), true);
    }

    @Transactional
    public RecruiterView update(Long userId, String publicId, RecruiterPatchRequest request) {
        RecruiterEntity entity = owned(userId, publicId);
        if (!entity.getVersion().equals(request.version())) {
            throw new BusinessException(4095301, "Recruiter version conflict", HttpStatus.CONFLICT);
        }
        if (request.position() != null) entity.setPosition(trim(request.position()));
        if (request.contactMasked() != null) entity.setContactMasked(trim(request.contactMasked()));
        if (request.email() != null) entity.setEmail(trim(request.email()));
        if (request.phone() != null) entity.setPhone(trim(request.phone()));
        if (request.nextFollowUpAt() != null) entity.setNextFollowUpAt(request.nextFollowUpAt());
        if (request.communicationStatus() != null) entity.setCommunicationStatus(request.communicationStatus());
        if (request.notes() != null) entity.setNotes(trim(request.notes()));
        mapper.updateById(entity);
        audit.record(userId, "RECRUITER_UPDATE", "RECRUITER", publicId);
        return get(userId, publicId);
    }

    public List<InteractionView> interactions(Long userId, String recruiterId) {
        RecruiterEntity recruiter = owned(userId, recruiterId);
        return interactions(userId, recruiter.getId());
    }

    @Transactional
    public InteractionView addInteraction(Long userId, String recruiterId, InteractionRequest request) {
        RecruiterEntity recruiter = owned(userId, recruiterId);
        ApplicationEntity application = request.applicationId() == null || request.applicationId().isBlank()
                ? null : applicationService.owned(userId, request.applicationId());
        RecruiterInteractionEntity entity = new RecruiterInteractionEntity();
        entity.setUserId(userId);
        entity.setRecruiterId(recruiter.getId());
        entity.setApplicationId(application == null ? null : application.getId());
        entity.setChannel(request.channel());
        entity.setDirection(request.direction());
        entity.setOccurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        entity.setSummary(request.summary().trim());
        entity.setFollowUpAt(request.followUpAt());
        interactionMapper.insert(entity);
        recruiter.setLastContactAt(entity.getOccurredAt());
        if (request.followUpAt() != null) {
            recruiter.setNextFollowUpAt(request.followUpAt());
            recruiter.setCommunicationStatus("FOLLOW_UP");
        } else if ("INBOUND".equals(request.direction())) {
            recruiter.setCommunicationStatus("REPLIED");
        } else {
            recruiter.setCommunicationStatus("CONTACTED");
        }
        mapper.updateById(recruiter);
        audit.record(userId, "RECRUITER_INTERACTION_CREATE", "RECRUITER", recruiterId);
        return interactionView(entity, application);
    }

    private RecruiterEntity owned(Long userId, String publicId) {
        RecruiterEntity entity = mapper.selectOne(new LambdaQueryWrapper<RecruiterEntity>()
                .eq(RecruiterEntity::getUserId, userId).eq(RecruiterEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Recruiter");
        return entity;
    }

    private RecruiterView view(Long userId, RecruiterEntity entity, boolean includeInteractions) {
        CompanyEntity company = entity.getCompanyId() == null ? null : companyMapper.selectById(entity.getCompanyId());
        return new RecruiterView(entity.getPublicId(), company == null ? null : company.getPublicId(),
                company == null ? null : company.getDisplayName(), entity.getName(), entity.getPosition(),
                entity.getPlatform(), entity.getPlatformRecruiterId(), entity.getContactMasked(),
                entity.getEmail(), entity.getPhone(), entity.getLastContactAt(), entity.getNextFollowUpAt(),
                entity.getCommunicationStatus(), entity.getNotes(), entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                includeInteractions ? interactions(userId, entity.getId()) : List.of());
    }

    private List<InteractionView> interactions(Long userId, Long recruiterId) {
        return interactionMapper.selectList(new LambdaQueryWrapper<RecruiterInteractionEntity>()
                        .eq(RecruiterInteractionEntity::getUserId, userId)
                        .eq(RecruiterInteractionEntity::getRecruiterId, recruiterId)
                        .orderByDesc(RecruiterInteractionEntity::getOccurredAt)
                        .orderByDesc(RecruiterInteractionEntity::getId))
                .stream().map(entity -> interactionView(entity,
                        entity.getApplicationId() == null ? null : applicationById(userId, entity.getApplicationId())))
                .toList();
    }

    private ApplicationEntity applicationById(Long userId, Long id) {
        return applicationService.ownedById(userId, id);
    }

    private InteractionView interactionView(RecruiterInteractionEntity entity, ApplicationEntity application) {
        return new InteractionView(entity.getPublicId(), application == null ? null : application.getPublicId(),
                entity.getChannel(), entity.getDirection(), entity.getOccurredAt(), entity.getSummary(),
                entity.getFollowUpAt(), entity.getCreatedAt());
    }

    private CompanyEntity company(String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        CompanyEntity company = companyMapper.selectOne(new LambdaQueryWrapper<CompanyEntity>()
                .eq(CompanyEntity::getPublicId, publicId).last("LIMIT 1"));
        if (company == null) throw new ResourceNotFoundException("Company");
        return company;
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upper(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(); }
}

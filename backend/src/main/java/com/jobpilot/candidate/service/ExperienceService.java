package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.dto.CandidateDtos.ExperienceRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ExperienceView;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceService {

    private final ExperienceMapper mapper;
    private final CandidateProfileService profileService;
    private final AuditService auditService;
    private final JsonCodec jsonCodec;

    public ExperienceService(ExperienceMapper mapper, CandidateProfileService profileService, AuditService auditService, JsonCodec jsonCodec) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.auditService = auditService;
        this.jsonCodec = jsonCodec;
    }

    public List<ExperienceView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<ExperienceEntity>()
                        .eq(ExperienceEntity::getUserId, userId)
                        .orderByAsc(ExperienceEntity::getSortOrder)
                        .orderByDesc(ExperienceEntity::getStartDate))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public ExperienceView create(Long userId, ExperienceRequest request) {
        validate(request);
        profileService.getOrCreate(userId);
        ExperienceEntity entity = new ExperienceEntity();
        entity.setUserId(userId);
        apply(entity, request);
        mapper.insert(entity);
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "EXPERIENCE_CREATE", "EXPERIENCE", entity.getPublicId());
        return toView(entity);
    }

    @Transactional
    public ExperienceView update(Long userId, String publicId, ExperienceRequest request) {
        validate(request);
        ExperienceEntity entity = owned(userId, publicId);
        apply(entity, request);
        if (mapper.updateById(entity) != 1) {
            throw new ValidationException("Experience was modified concurrently; reload and retry");
        }
        auditService.record(userId, "EXPERIENCE_UPDATE", "EXPERIENCE", publicId);
        return toView(entity);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        ExperienceEntity entity = owned(userId, publicId);
        mapper.deleteById(entity.getId());
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "EXPERIENCE_DELETE", "EXPERIENCE", publicId);
    }

    private void validate(ExperienceRequest request) {
        boolean current = Boolean.TRUE.equals(request.currentlyWorking());
        if (!current && request.endDate() == null) {
            throw new ValidationException("endDate is required when currentlyWorking is false");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate must not be before startDate");
        }
    }

    private ExperienceEntity owned(Long userId, String publicId) {
        ExperienceEntity entity = mapper.selectOne(new LambdaQueryWrapper<ExperienceEntity>()
                .eq(ExperienceEntity::getPublicId, publicId)
                .eq(ExperienceEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ResourceNotFoundException("Experience");
        }
        return entity;
    }

    private void apply(ExperienceEntity entity, ExperienceRequest request) {
        entity.setCompanyName(request.companyName().trim());
        entity.setRole(request.role().trim());
        entity.setEmploymentType(request.employmentType().name());
        entity.setLocation(request.location());
        entity.setStartDate(request.startDate());
        entity.setCurrentlyWorking(Boolean.TRUE.equals(request.currentlyWorking()));
        entity.setEndDate(Boolean.TRUE.equals(request.currentlyWorking()) ? null : request.endDate());
        entity.setDescription(request.description());
        entity.setResponsibilities(request.responsibilities());
        entity.setAchievements(request.achievements());
        entity.setTechnologiesJson(jsonCodec.write(request.technologies() == null ? List.of() : request.technologies()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private ExperienceView toView(ExperienceEntity entity) {
        return new ExperienceView(entity.getPublicId(), entity.getCompanyName(), entity.getRole(),
                com.jobpilot.candidate.domain.EmploymentType.valueOf(entity.getEmploymentType()), entity.getLocation(),
                entity.getStartDate(), entity.getEndDate(), entity.getCurrentlyWorking(), entity.getDescription(),
                entity.getResponsibilities(), entity.getAchievements(), jsonCodec.readStringList(entity.getTechnologiesJson()),
                entity.getSortOrder(), entity.getVersion());
    }
}


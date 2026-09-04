package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.dto.CandidateDtos.ProjectRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ProjectView;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectMapper mapper;
    private final CandidateProfileService profileService;
    private final AuditService auditService;
    private final JsonCodec jsonCodec;

    public ProjectService(ProjectMapper mapper, CandidateProfileService profileService, AuditService auditService, JsonCodec jsonCodec) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.auditService = auditService;
        this.jsonCodec = jsonCodec;
    }

    public List<ProjectView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getUserId, userId)
                        .orderByDesc(ProjectEntity::getFeatured)
                        .orderByAsc(ProjectEntity::getSortOrder)
                        .orderByDesc(ProjectEntity::getStartDate))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public ProjectView create(Long userId, ProjectRequest request) {
        validateDates(request);
        profileService.getOrCreate(userId);
        ProjectEntity entity = new ProjectEntity();
        entity.setUserId(userId);
        apply(entity, request);
        mapper.insert(entity);
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "PROJECT_CREATE", "PROJECT", entity.getPublicId());
        return toView(entity);
    }

    @Transactional
    public ProjectView update(Long userId, String publicId, ProjectRequest request) {
        validateDates(request);
        ProjectEntity entity = owned(userId, publicId);
        apply(entity, request);
        if (mapper.updateById(entity) != 1) {
            throw new ValidationException("Project was modified concurrently; reload and retry");
        }
        auditService.record(userId, "PROJECT_UPDATE", "PROJECT", publicId);
        return toView(entity);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        ProjectEntity entity = owned(userId, publicId);
        mapper.deleteById(entity.getId());
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "PROJECT_DELETE", "PROJECT", publicId);
    }

    private ProjectEntity owned(Long userId, String publicId) {
        ProjectEntity entity = mapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getPublicId, publicId)
                .eq(ProjectEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ResourceNotFoundException("Project");
        }
        return entity;
    }

    private void validateDates(ProjectRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate must not be before startDate");
        }
    }

    private void apply(ProjectEntity entity, ProjectRequest request) {
        entity.setName(request.name().trim());
        entity.setRole(request.role());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setDescription(request.description().trim());
        entity.setBackground(request.background());
        entity.setResponsibilities(request.responsibilities());
        entity.setAchievements(request.achievements());
        entity.setTechnologiesJson(jsonCodec.write(request.technologies() == null ? List.of() : request.technologies()));
        entity.setRepoUrl(request.repoUrl());
        entity.setDemoUrl(request.demoUrl());
        entity.setFeatured(Boolean.TRUE.equals(request.featured()));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private ProjectView toView(ProjectEntity entity) {
        return new ProjectView(entity.getPublicId(), entity.getName(), entity.getRole(), entity.getStartDate(),
                entity.getEndDate(), entity.getDescription(), entity.getBackground(), entity.getResponsibilities(),
                entity.getAchievements(), jsonCodec.readStringList(entity.getTechnologiesJson()), entity.getRepoUrl(),
                entity.getDemoUrl(), entity.getFeatured(), entity.getSortOrder(), entity.getVersion());
    }
}


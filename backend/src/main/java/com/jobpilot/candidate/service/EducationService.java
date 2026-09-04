package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.dto.CandidateDtos.EducationRequest;
import com.jobpilot.candidate.dto.CandidateDtos.EducationView;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationService {

    private final EducationMapper mapper;
    private final CandidateProfileService profileService;
    private final AuditService auditService;

    public EducationService(EducationMapper mapper, CandidateProfileService profileService, AuditService auditService) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.auditService = auditService;
    }

    public List<EducationView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<EducationEntity>()
                        .eq(EducationEntity::getUserId, userId)
                        .orderByAsc(EducationEntity::getSortOrder)
                        .orderByDesc(EducationEntity::getStartDate))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public EducationView create(Long userId, EducationRequest request) {
        validateDates(request.startDate(), request.endDate());
        profileService.getOrCreate(userId);
        EducationEntity entity = new EducationEntity();
        entity.setUserId(userId);
        apply(entity, request);
        mapper.insert(entity);
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "EDUCATION_CREATE", "EDUCATION", entity.getPublicId());
        return toView(entity);
    }

    @Transactional
    public EducationView update(Long userId, String publicId, EducationRequest request) {
        validateDates(request.startDate(), request.endDate());
        EducationEntity entity = owned(userId, publicId);
        apply(entity, request);
        if (mapper.updateById(entity) != 1) {
            throw new ValidationException("Education was modified concurrently; reload and retry");
        }
        auditService.record(userId, "EDUCATION_UPDATE", "EDUCATION", publicId);
        return toView(entity);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        EducationEntity entity = owned(userId, publicId);
        mapper.deleteById(entity.getId());
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "EDUCATION_DELETE", "EDUCATION", publicId);
    }

    private EducationEntity owned(Long userId, String publicId) {
        EducationEntity entity = mapper.selectOne(new LambdaQueryWrapper<EducationEntity>()
                .eq(EducationEntity::getPublicId, publicId)
                .eq(EducationEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ResourceNotFoundException("Education");
        }
        return entity;
    }

    private void apply(EducationEntity entity, EducationRequest request) {
        entity.setSchool(request.school().trim());
        entity.setDegree(request.degree().trim());
        entity.setMajor(request.major().trim());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setGraduationYear(request.graduationYear());
        entity.setDescription(request.description());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new ValidationException("endDate must not be before startDate");
        }
    }

    private EducationView toView(EducationEntity entity) {
        return new EducationView(entity.getPublicId(), entity.getSchool(), entity.getDegree(), entity.getMajor(),
                entity.getStartDate(), entity.getEndDate(), entity.getGraduationYear(), entity.getDescription(),
                entity.getSortOrder(), entity.getVersion());
    }
}


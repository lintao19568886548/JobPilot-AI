package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.SkillCategory;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillCreateRequest;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillUpdateRequest;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillView;
import com.jobpilot.candidate.dto.CandidateDtos.SkillView;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillService {

    private final SkillMapper skillMapper;
    private final CandidateSkillMapper candidateSkillMapper;
    private final CandidateProfileService profileService;
    private final AuditService auditService;

    public SkillService(
            SkillMapper skillMapper,
            CandidateSkillMapper candidateSkillMapper,
            CandidateProfileService profileService,
            AuditService auditService) {
        this.skillMapper = skillMapper;
        this.candidateSkillMapper = candidateSkillMapper;
        this.profileService = profileService;
        this.auditService = auditService;
    }

    public List<SkillView> listSkills(String search, String category) {
        LambdaQueryWrapper<SkillEntity> query = new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getStatus, "ACTIVE")
                .orderByAsc(SkillEntity::getCategory)
                .orderByAsc(SkillEntity::getDisplayName);
        if (StringUtils.hasText(search)) {
            String value = search.trim();
            query.and(wrapper -> wrapper.like(SkillEntity::getDisplayName, value)
                    .or().like(SkillEntity::getCanonicalName, value));
        }
        if (StringUtils.hasText(category)) {
            try {
                query.eq(SkillEntity::getCategory, SkillCategory.valueOf(category.toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException exception) {
                throw new ValidationException("Unknown skill category");
            }
        }
        return skillMapper.selectList(query).stream().map(this::skillView).toList();
    }

    public List<CandidateSkillView> listCandidateSkills(Long userId) {
        return candidateSkillMapper.selectList(new LambdaQueryWrapper<CandidateSkillEntity>()
                        .eq(CandidateSkillEntity::getUserId, userId)
                        .orderByDesc(CandidateSkillEntity::getPrimarySkill)
                        .orderByDesc(CandidateSkillEntity::getProficiency))
                .stream().map(this::candidateSkillView).toList();
    }

    @Transactional
    public CandidateSkillView create(Long userId, CandidateSkillCreateRequest request) {
        CandidateProfileEntity profile = profileService.getOrCreate(userId);
        SkillEntity skill = skillByPublicId(request.skillId());
        long duplicates = candidateSkillMapper.selectCount(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId)
                .eq(CandidateSkillEntity::getSkillId, skill.getId()));
        if (duplicates > 0) {
            throw new BusinessException(4092001, "Skill is already linked to candidate", HttpStatus.CONFLICT);
        }
        CandidateSkillEntity entity = new CandidateSkillEntity();
        entity.setUserId(userId);
        entity.setCandidateProfileId(profile.getId());
        entity.setSkillId(skill.getId());
        entity.setProficiency(request.proficiency());
        entity.setYears(request.years());
        entity.setLastUsedAt(request.lastUsedAt());
        entity.setSource(normalizeSource(request.source()));
        entity.setPrimarySkill(Boolean.TRUE.equals(request.primary()));
        candidateSkillMapper.insert(entity);
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "SKILL_UPDATE", "CANDIDATE_SKILL", entity.getPublicId());
        return new CandidateSkillView(entity.getPublicId(), skillView(skill), entity.getProficiency(), entity.getYears(),
                entity.getLastUsedAt(), entity.getSource(), entity.getPrimarySkill(), entity.getVersion());
    }

    @Transactional
    public CandidateSkillView update(Long userId, String publicId, CandidateSkillUpdateRequest request) {
        CandidateSkillEntity entity = owned(userId, publicId);
        entity.setProficiency(request.proficiency());
        entity.setYears(request.years());
        entity.setLastUsedAt(request.lastUsedAt());
        entity.setSource(normalizeSource(request.source()));
        entity.setPrimarySkill(Boolean.TRUE.equals(request.primary()));
        if (candidateSkillMapper.updateById(entity) != 1) {
            throw new ValidationException("Skill was modified concurrently; reload and retry");
        }
        auditService.record(userId, "SKILL_UPDATE", "CANDIDATE_SKILL", publicId);
        return candidateSkillView(entity);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        CandidateSkillEntity entity = owned(userId, publicId);
        candidateSkillMapper.deleteById(entity.getId());
        profileService.refreshCompleteness(userId);
        auditService.record(userId, "SKILL_DELETE", "CANDIDATE_SKILL", publicId);
    }

    private CandidateSkillEntity owned(Long userId, String publicId) {
        CandidateSkillEntity entity = candidateSkillMapper.selectOne(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getPublicId, publicId)
                .eq(CandidateSkillEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new ResourceNotFoundException("Candidate skill");
        }
        return entity;
    }

    private SkillEntity skillByPublicId(String publicId) {
        SkillEntity skill = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getPublicId, publicId)
                .eq(SkillEntity::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (skill == null) {
            throw new ResourceNotFoundException("Skill");
        }
        return skill;
    }

    private CandidateSkillView candidateSkillView(CandidateSkillEntity entity) {
        SkillEntity skill = skillMapper.selectById(entity.getSkillId());
        if (skill == null) {
            throw new IllegalStateException("Candidate skill references a missing skill");
        }
        return new CandidateSkillView(entity.getPublicId(), skillView(skill), entity.getProficiency(), entity.getYears(),
                entity.getLastUsedAt(), entity.getSource(), entity.getPrimarySkill(), entity.getVersion());
    }

    private SkillView skillView(SkillEntity entity) {
        return new SkillView(entity.getPublicId(), entity.getCanonicalName(), entity.getDisplayName(),
                SkillCategory.valueOf(entity.getCategory()), entity.getDescription());
    }

    private String normalizeSource(String source) {
        return StringUtils.hasText(source) ? source.trim() : "USER";
    }
}


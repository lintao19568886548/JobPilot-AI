package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.common.util.JsonCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CandidateProfileCompletenessService {

    private final EducationMapper educationMapper;
    private final ExperienceMapper experienceMapper;
    private final ProjectMapper projectMapper;
    private final CandidateSkillMapper candidateSkillMapper;
    private final JsonCodec jsonCodec;

    public CandidateProfileCompletenessService(
            EducationMapper educationMapper,
            ExperienceMapper experienceMapper,
            ProjectMapper projectMapper,
            CandidateSkillMapper candidateSkillMapper,
            JsonCodec jsonCodec) {
        this.educationMapper = educationMapper;
        this.experienceMapper = experienceMapper;
        this.projectMapper = projectMapper;
        this.candidateSkillMapper = candidateSkillMapper;
        this.jsonCodec = jsonCodec;
    }

    public Map<String, Integer> breakdown(CandidateProfileEntity profile) {
        Long userId = profile.getUserId();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        int basic = present(profile.getFullName()) * 5
                + present(profile.getEmail()) * 5
                + present(profile.getCurrentCity()) * 5
                + present(profile.getHeadline()) * 5;
        int education = educationMapper.selectCount(new LambdaQueryWrapper<EducationEntity>()
                .eq(EducationEntity::getUserId, userId)) > 0 ? 15 : 0;
        long skillCount = candidateSkillMapper.selectCount(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId));
        int skills = (int) Math.min(20, skillCount * 4);
        int projects = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId)) > 0 ? 20 : 0;
        int experiences = experienceMapper.selectCount(new LambdaQueryWrapper<ExperienceEntity>()
                .eq(ExperienceEntity::getUserId, userId)) > 0 ? 15 : 0;
        int preferences = 0;
        preferences += jsonCodec.readStringList(profile.getTargetRolesJson()).isEmpty() ? 0 : 4;
        preferences += jsonCodec.readStringList(profile.getTargetCitiesJson()).isEmpty() ? 0 : 2;
        preferences += profile.getTargetSalaryMin() == null || profile.getTargetSalaryMax() == null ? 0 : 2;
        preferences += profile.getSalaryCurrency() == null ? 0 : 1;
        preferences += profile.getAcceptRemote() != null || profile.getAcceptRelocation() != null ? 1 : 0;
        result.put("basic", basic);
        result.put("education", education);
        result.put("skills", skills);
        result.put("projects", projects);
        result.put("experience", experiences);
        result.put("preferences", preferences);
        return result;
    }

    public int score(CandidateProfileEntity profile) {
        return breakdown(profile).values().stream().mapToInt(Integer::intValue).sum();
    }

    private int present(String value) {
        return value == null || value.isBlank() ? 0 : 1;
    }
}


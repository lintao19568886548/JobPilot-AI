package com.jobpilot.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.common.util.JsonCodec;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateProfileCompletenessServiceTest {

    private EducationMapper educationMapper;
    private ExperienceMapper experienceMapper;
    private ProjectMapper projectMapper;
    private CandidateSkillMapper candidateSkillMapper;
    private CandidateProfileCompletenessService service;

    @BeforeEach
    void setUp() {
        educationMapper = mock(EducationMapper.class);
        experienceMapper = mock(ExperienceMapper.class);
        projectMapper = mock(ProjectMapper.class);
        candidateSkillMapper = mock(CandidateSkillMapper.class);
        service = new CandidateProfileCompletenessService(educationMapper, experienceMapper, projectMapper,
                candidateSkillMapper, new JsonCodec(new ObjectMapper()));
    }

    @Test
    void emptyProfileHasOnlyPreferenceDefaults() {
        CandidateProfileEntity profile = baseProfile();
        assertThat(service.score(profile)).isEqualTo(2);
    }

    @Test
    void completeProfileScoresOneHundred() {
        CandidateProfileEntity profile = baseProfile();
        profile.setFullName("Lin Tao");
        profile.setEmail("lin@example.com");
        profile.setCurrentCity("Shanghai");
        profile.setHeadline("Backend Engineer");
        profile.setTargetRolesJson("[\"Java Engineer\"]");
        profile.setTargetCitiesJson("[\"Shanghai\"]");
        profile.setTargetSalaryMin(new BigDecimal("20000"));
        profile.setTargetSalaryMax(new BigDecimal("30000"));
        when(educationMapper.selectCount(any())).thenReturn(1L);
        when(experienceMapper.selectCount(any())).thenReturn(1L);
        when(projectMapper.selectCount(any())).thenReturn(1L);
        when(candidateSkillMapper.selectCount(any())).thenReturn(5L);
        assertThat(service.score(profile)).isEqualTo(100);
        assertThat(service.breakdown(profile)).containsEntry("skills", 20).containsEntry("projects", 20);
    }

    private CandidateProfileEntity baseProfile() {
        CandidateProfileEntity profile = new CandidateProfileEntity();
        profile.setUserId(1L);
        profile.setTargetRolesJson("[]");
        profile.setTargetCitiesJson("[]");
        profile.setSalaryCurrency("CNY");
        profile.setAcceptRemote(false);
        profile.setAcceptRelocation(false);
        return profile;
    }
}

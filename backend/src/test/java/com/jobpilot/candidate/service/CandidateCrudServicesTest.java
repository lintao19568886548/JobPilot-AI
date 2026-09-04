package com.jobpilot.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.domain.EmploymentType;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillCreateRequest;
import com.jobpilot.candidate.dto.CandidateDtos.EducationRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ExperienceRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ProjectRequest;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateCrudServicesTest {

    private EducationMapper educationMapper;
    private ExperienceMapper experienceMapper;
    private ProjectMapper projectMapper;
    private SkillMapper skillMapper;
    private CandidateSkillMapper candidateSkillMapper;
    private CandidateProfileService profileService;
    private AuditService audit;
    private JsonCodec json;

    @BeforeEach
    void setUp() {
        educationMapper = mock(EducationMapper.class);
        experienceMapper = mock(ExperienceMapper.class);
        projectMapper = mock(ProjectMapper.class);
        skillMapper = mock(SkillMapper.class);
        candidateSkillMapper = mock(CandidateSkillMapper.class);
        profileService = mock(CandidateProfileService.class);
        audit = mock(AuditService.class);
        json = new JsonCodec(new ObjectMapper());
        CandidateProfileEntity profile = new CandidateProfileEntity();
        profile.setId(5L);
        profile.setUserId(1L);
        when(profileService.getOrCreate(1L)).thenReturn(profile);
    }

    @Test
    void educationCreateMapsAndReturnsDatabaseIdentity() {
        doAnswer(invocation -> assign(invocation.getArgument(0), 11L, "education-1"))
                .when(educationMapper).insert(any(EducationEntity.class));
        var service = new EducationService(educationMapper, profileService, audit);
        var result = service.create(1L, educationRequest(LocalDate.of(2020, 9, 1), LocalDate.of(2024, 6, 30)));
        assertThat(result.id()).isEqualTo("education-1");
        assertThat(result.school()).isEqualTo("Example University");
    }

    @Test
    void educationRejectsInvalidDates() {
        var service = new EducationService(educationMapper, profileService, audit);
        assertThatThrownBy(() -> service.create(1L,
                educationRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void educationUpdateEnforcesOwnership() {
        var service = new EducationService(educationMapper, profileService, audit);
        assertThatThrownBy(() -> service.update(99L, "education-1",
                educationRequest(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void experienceCreatePreservesTechnologyArray() {
        doAnswer(invocation -> assign(invocation.getArgument(0), 12L, "experience-1"))
                .when(experienceMapper).insert(any(ExperienceEntity.class));
        var service = new ExperienceService(experienceMapper, profileService, audit, json);
        var result = service.create(1L, experienceRequest(false, LocalDate.of(2024, 6, 1)));
        assertThat(result.technologies()).containsExactly("Java", "Redis");
    }

    @Test
    void experienceRequiresEndDateForPastRole() {
        var service = new ExperienceService(experienceMapper, profileService, audit, json);
        assertThatThrownBy(() -> service.create(1L, experienceRequest(false, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void projectCreatePreservesFeaturedAndTechnologyArray() {
        doAnswer(invocation -> assign(invocation.getArgument(0), 13L, "project-1"))
                .when(projectMapper).insert(any(ProjectEntity.class));
        var service = new ProjectService(projectMapper, profileService, audit, json);
        var result = service.create(1L, projectRequest(LocalDate.of(2025, 6, 1)));
        assertThat(result.featured()).isTrue();
        assertThat(result.technologies()).containsExactly("Spring Boot", "Vue 3");
    }

    @Test
    void projectRejectsInvalidDates() {
        var service = new ProjectService(projectMapper, profileService, audit, json);
        assertThatThrownBy(() -> service.create(1L, projectRequest(LocalDate.of(2023, 6, 1))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void skillCreateUsesCanonicalCatalogAndCandidateRelation() {
        SkillEntity skill = new SkillEntity();
        skill.setId(20L);
        skill.setPublicId("skill-java");
        skill.setCanonicalName("java");
        skill.setDisplayName("Java");
        skill.setCategory("LANGUAGE");
        when(skillMapper.selectOne(any())).thenReturn(skill);
        when(candidateSkillMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> assign(invocation.getArgument(0), 21L, "candidate-skill-1"))
                .when(candidateSkillMapper).insert(any(CandidateSkillEntity.class));
        var service = new SkillService(skillMapper, candidateSkillMapper, profileService, audit);
        var result = service.create(1L,
                new CandidateSkillCreateRequest("skill-java", 95, new BigDecimal("3.0"),
                        LocalDate.of(2026, 1, 1), "USER", true));
        assertThat(result.skill().canonicalName()).isEqualTo("java");
        assertThat(result.proficiency()).isEqualTo(95);
    }

    @Test
    void skillCategoryValidationRejectsUnknownValue() {
        var service = new SkillService(skillMapper, candidateSkillMapper, profileService, audit);
        assertThatThrownBy(() -> service.listSkills(null, "UNKNOWN"))
                .isInstanceOf(ValidationException.class);
    }

    private EducationRequest educationRequest(LocalDate start, LocalDate end) {
        return new EducationRequest(" Example University ", "Bachelor", "Software Engineering", start, end,
                2024, "Coursework", 0);
    }

    private ExperienceRequest experienceRequest(boolean current, LocalDate end) {
        return new ExperienceRequest("Example Corp", "Backend Intern", EmploymentType.INTERNSHIP, "Shanghai",
                LocalDate.of(2024, 1, 1), end, current, "Built services", "APIs", "Reduced latency",
                List.of("Java", "Redis"), 0);
    }

    private ProjectRequest projectRequest(LocalDate end) {
        return new ProjectRequest("JobPilot AI", "Lead", LocalDate.of(2024, 1, 1), end, "Career workspace",
                "Local-first", "Architecture", "Phase 1", List.of("Spring Boot", "Vue 3"),
                "https://github.com/example/jobpilot", "https://example.com/jobpilot", true, 0);
    }

    private int assign(Object value, long id, String publicId) {
        com.jobpilot.common.persistence.BaseEntity entity = (com.jobpilot.common.persistence.BaseEntity) value;
        entity.setId(id);
        entity.setPublicId(publicId);
        entity.setVersion(0);
        return 1;
    }
}

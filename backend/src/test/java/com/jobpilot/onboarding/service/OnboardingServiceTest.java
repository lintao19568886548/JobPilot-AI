package com.jobpilot.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.candidate.dto.CandidateDtos.ProfileView;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.service.CandidateProfileService;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.service.ResumeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnboardingServiceTest {

    private CandidateProfileService profileService;
    private CandidateSkillMapper skillMapper;
    private EducationMapper educationMapper;
    private ExperienceMapper experienceMapper;
    private ProjectMapper projectMapper;
    private ResumeService resumeService;
    private OnboardingService service;

    @BeforeEach
    void setUp() {
        profileService = mock(CandidateProfileService.class);
        skillMapper = mock(CandidateSkillMapper.class);
        educationMapper = mock(EducationMapper.class);
        experienceMapper = mock(ExperienceMapper.class);
        projectMapper = mock(ProjectMapper.class);
        resumeService = mock(ResumeService.class);
        service = new OnboardingService(profileService, skillMapper, educationMapper, experienceMapper, projectMapper, resumeService);
        when(resumeService.list(any())).thenReturn(List.of());
    }

    @Test
    void emptyProfileHasZeroReadinessAndOrderedBlockers() {
        when(profileService.get(7L)).thenReturn(profile(false));
        counts(0, 0, 0, 0, 0);

        var overview = service.overview(7L);

        assertThat(overview.score()).isZero();
        assertThat(overview.status()).isEqualTo("NEEDS_WORK");
        assertThat(overview.readyForMatching()).isFalse();
        assertThat(overview.steps()).hasSize(9).allMatch(step -> step.earnedScore() == 0);
        assertThat(overview.qualitySummary().blockers()).isEqualTo(4);
        assertThat(service.dataQuality(7L).issues())
                .extracting(item -> item.severity() + ":" + item.code())
                .startsWith(
                        "BLOCKER:MASTER_RESUME_MISSING",
                        "BLOCKER:PROFILE_EMAIL_MISSING",
                        "BLOCKER:PROFILE_NAME_MISSING",
                        "BLOCKER:RESUME_VERSION_MISSING");
    }

    @Test
    void partialFactsEarnOnlyDeterministicPartialScore() {
        when(profileService.get(9L)).thenReturn(profile(true));
        counts(3, 1, 1, 0, 1);
        when(resumeService.list(9L)).thenReturn(List.of(resume(true, false, true)));

        var overview = service.overview(9L);

        assertThat(overview.score()).isEqualTo(72);
        assertThat(overview.steps()).filteredOn(step -> step.key().equals("SKILLS"))
                .singleElement().satisfies(step -> {
                    assertThat(step.earnedScore()).isEqualTo(12);
                    assertThat(step.status()).isEqualTo("IN_PROGRESS");
                });
        assertThat(overview.qualitySummary().blockers()).isZero();
        assertThat(overview.status()).isEqualTo("NEEDS_WORK");
    }

    @Test
    void completeFactsAreReadyAndProduceNoSyntheticIssues() {
        when(profileService.get(11L)).thenReturn(profile(true));
        counts(5, 2, 1, 1, 2);
        when(resumeService.list(11L)).thenReturn(List.of(resume(true, true, true)));

        var overview = service.overview(11L);
        var quality = service.dataQuality(11L);

        assertThat(overview.score()).isEqualTo(100);
        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.readyForMatching()).isTrue();
        assertThat(overview.completedSteps()).isEqualTo(9);
        assertThat(quality.issues()).isEmpty();
        assertThat(quality.summary().total()).isZero();
    }

    @Test
    void masterDefaultAndVersionAreCheckedIndependently() {
        when(profileService.get(13L)).thenReturn(profile(true));
        counts(5, 2, 1, 1, 2);
        when(resumeService.list(13L)).thenReturn(List.of(resume(true, false, false)));

        assertThat(service.dataQuality(13L).issues()).extracting(item -> item.code())
                .containsExactly("RESUME_VERSION_MISSING", "DEFAULT_RESUME_MISSING");
    }

    @Test
    void everyQueryUsesTheAuthenticatedUserId() {
        when(profileService.get(42L)).thenReturn(profile(true));
        counts(5, 2, 1, 1, 2);

        service.overview(42L);

        verify(profileService).get(42L);
        verify(resumeService).list(42L);
        verify(skillMapper, org.mockito.Mockito.times(2)).selectCount(any());
        verify(educationMapper).selectCount(any());
        verify(experienceMapper).selectCount(any());
        verify(projectMapper).selectCount(any());
    }

    private void counts(long skills, long primarySkills, long educations, long experiences, long projects) {
        when(skillMapper.selectCount(any())).thenReturn(skills, primarySkills, skills, primarySkills);
        when(educationMapper.selectCount(any())).thenReturn(educations);
        when(experienceMapper.selectCount(any())).thenReturn(experiences);
        when(projectMapper.selectCount(any())).thenReturn(projects);
    }

    private ProfileView profile(boolean complete) {
        return new ProfileView(
                "profile-1",
                complete ? "Lin" : null,
                complete ? "Java Backend Engineer" : null,
                null,
                complete ? "lin@example.test" : null,
                complete ? "Shanghai" : null,
                complete ? List.of("Shanghai") : List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                complete ? List.of("Java Backend Engineer") : List.of(),
                List.of(),
                List.of(),
                complete ? new BigDecimal("35000") : null,
                complete ? new BigDecimal("50000") : null,
                "CNY",
                false,
                false,
                complete ? 100 : 0,
                0,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private ResumeSummaryView resume(boolean master, boolean defaultResume, boolean currentVersion) {
        return new ResumeSummaryView(
                "resume-1", "Master Resume", "Java Backend Engineer", master, defaultResume,
                null, "ACTIVE", currentVersion ? "version-1" : null, currentVersion ? 1 : null,
                currentVersion ? 1 : 0, 0, LocalDateTime.now(), LocalDateTime.now());
    }
}

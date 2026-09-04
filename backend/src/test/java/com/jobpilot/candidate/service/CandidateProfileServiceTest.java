package com.jobpilot.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileRequest;
import com.jobpilot.candidate.mapper.CandidateProfileMapper;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateProfileServiceTest {

    private CandidateProfileMapper mapper;
    private CandidateProfileCompletenessService completeness;
    private AuditService audit;
    private CandidateProfileService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CandidateProfileMapper.class);
        completeness = mock(CandidateProfileCompletenessService.class);
        audit = mock(AuditService.class);
        service = new CandidateProfileService(mapper, completeness, new JsonCodec(new ObjectMapper()), audit);
    }

    @Test
    void createsPersistentEmptyProfileWithSafeDefaults() {
        doAnswer(invocation -> {
            CandidateProfileEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            entity.setPublicId("profile-1");
            entity.setVersion(0);
            return 1;
        }).when(mapper).insert(any(CandidateProfileEntity.class));
        CandidateProfileEntity result = service.getOrCreate(9L);
        assertThat(result.getUserId()).isEqualTo(9L);
        assertThat(result.getTargetRolesJson()).isEqualTo("[]");
        assertThat(result.getSalaryCurrency()).isEqualTo("CNY");
        verify(mapper).insert(result);
    }

    @Test
    void rejectsInvertedSalaryRange() {
        ProfileRequest request = request(new BigDecimal("30000"), new BigDecimal("20000"));
        assertThatThrownBy(() -> service.replace(1L, request)).isInstanceOf(ValidationException.class);
    }

    @Test
    void replacesProfileAndPersistsComputedCompleteness() {
        CandidateProfileEntity profile = new CandidateProfileEntity();
        profile.setId(3L);
        profile.setPublicId("profile-3");
        profile.setUserId(1L);
        profile.setVersion(0);
        when(mapper.selectOne(any())).thenReturn(profile);
        when(completeness.score(profile)).thenReturn(36);
        when(mapper.updateById(profile)).thenReturn(1);
        var view = service.replace(1L, request(new BigDecimal("20000"), new BigDecimal("30000")));
        assertThat(view.fullName()).isEqualTo("Lin Tao");
        assertThat(view.profileCompleteness()).isEqualTo(36);
        verify(audit).record(1L, "PROFILE_UPDATE", "CANDIDATE_PROFILE", "profile-3");
    }

    private ProfileRequest request(BigDecimal min, BigDecimal max) {
        return new ProfileRequest("Lin Tao", "Backend Engineer", "+8613800000000", "lin@example.com",
                "Shanghai", List.of("Shanghai"), 2026, "BACHELOR", "Example University", "Software Engineering",
                new BigDecimal("1.0"), "OPEN_TO_WORK", "https://github.com/example", "https://example.com",
                "Backend engineer", List.of("Java Engineer"), List.of("Software"), List.of("Technology"),
                min, max, "CNY", true, false);
    }
}

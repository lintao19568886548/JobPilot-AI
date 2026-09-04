package com.jobpilot.onboarding.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.common.security.JobPilotPrincipal;
import com.jobpilot.onboarding.dto.OnboardingDtos.DataQualityView;
import com.jobpilot.onboarding.dto.OnboardingDtos.OnboardingOverviewView;
import com.jobpilot.onboarding.dto.OnboardingDtos.QualitySummaryView;
import com.jobpilot.onboarding.service.OnboardingService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OnboardingControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewUsesPrincipalAndApiEnvelope() {
        OnboardingService service = mock(OnboardingService.class);
        var view = new OnboardingOverviewView(100, "READY", true, 9, 9, List.of(),
                new QualitySummaryView(0, 0, 0, 0), Instant.EPOCH);
        when(service.overview(77L)).thenReturn(view);
        authenticate(77L);

        var response = new OnboardingController(service).overview();

        assertThat(response.code()).isZero();
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isSameAs(view);
        verify(service).overview(77L);
    }

    @Test
    void dataQualityUsesPrincipalAndApiEnvelope() {
        OnboardingService service = mock(OnboardingService.class);
        var view = new DataQualityView(new QualitySummaryView(0, 0, 0, 0), List.of(), Instant.EPOCH);
        when(service.dataQuality(88L)).thenReturn(view);
        authenticate(88L);

        var response = new OnboardingController(service).dataQuality();

        assertThat(response.code()).isZero();
        assertThat(response.data()).isSameAs(view);
        verify(service).dataQuality(88L);
    }

    private void authenticate(Long userId) {
        var principal = new JobPilotPrincipal(userId, "user-public-id", "local-user");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}

package com.jobpilot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobpilot.application.domain.PlatformPolicyEntity;
import com.jobpilot.application.mapper.PlatformPolicyMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlatformPolicyServiceTest {
    private final PlatformPolicyMapper mapper = mock(PlatformPolicyMapper.class);
    private final PlatformPolicyService service = new PlatformPolicyService(mapper, mock(AuditService.class));

    @Test
    void unknownPlatformDefaultsToManualOnly() {
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        var policy = service.resolve("unknown-board");
        assertThat(policy.applicationMode()).isEqualTo("MANUAL_ONLY");
        assertThat(policy.requiresFinalConfirmation()).isTrue();
        assertThat(policy.configured()).isFalse();
    }

    @Test
    void stalePolicySafelyDowngradesToManual() {
        PlatformPolicyEntity policy = new PlatformPolicyEntity();
        policy.setPlatform("EXAMPLE");
        policy.setCollectionMode("VISIBLE_PAGE_ONLY");
        policy.setApplicationMode("ASSIST_ALLOWED");
        policy.setRequiresFinalConfirmation(true);
        policy.setReviewedAt(LocalDateTime.now().minusDays(181));
        policy.setStatus("ACTIVE");
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(policy);
        assertThat(service.resolve("example").applicationMode()).isEqualTo("MANUAL_ONLY");
    }

    @Test
    void phaseSevenStillRejectsAuthorizedAutomation() {
        assertThatThrownBy(() -> service.requireMode("MANUAL", "AUTHORIZED_AUTOMATION"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not executable in Phase 7");
    }
}

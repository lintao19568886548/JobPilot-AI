package com.jobpilot.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jobpilot.application.dto.ApplicationDtos.ApplicationCreateRequest;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class ApplicationSafetyBoundaryTest {
    @Test
    void refusesToCreateApplicationWithoutExplicitExternalConfirmation() {
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        ApplicationLogMapper logMapper = mock(ApplicationLogMapper.class);
        ApplicationQueueItemMapper queueMapper = mock(ApplicationQueueItemMapper.class);
        ApplicationService service = new ApplicationService(applicationMapper, logMapper, queueMapper,
                mock(ApplicationQueueService.class), mock(ApplicationReferenceService.class),
                mock(PlatformPolicyService.class), mock(StringRedisTemplate.class),
                mock(JsonCodec.class), mock(AuditService.class));
        ApplicationCreateRequest request = new ApplicationCreateRequest(
                "queue", null, null, null, "ASSIST", false, null, null, null, null);

        assertThatThrownBy(() -> service.create(1L, "confirm-key", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Explicit external submission confirmation");
        verifyNoInteractions(applicationMapper, logMapper, queueMapper);
    }
}

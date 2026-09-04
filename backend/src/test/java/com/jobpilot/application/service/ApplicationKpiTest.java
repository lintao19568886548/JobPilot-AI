package com.jobpilot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.util.JsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class ApplicationKpiTest {
    @Test
    void countsEachApplicationOnceForEveryStageItReached() {
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        ApplicationLogMapper logMapper = mock(ApplicationLogMapper.class);
        ApplicationQueueItemMapper queueMapper = mock(ApplicationQueueItemMapper.class);
        ApplicationService service = new ApplicationService(applicationMapper, logMapper, queueMapper,
                mock(ApplicationQueueService.class), mock(ApplicationReferenceService.class),
                mock(PlatformPolicyService.class), mock(StringRedisTemplate.class),
                mock(JsonCodec.class), mock(AuditService.class));

        ApplicationQueueItemEntity activeQueue = new ApplicationQueueItemEntity();
        activeQueue.setStatus("APPROVED");
        ApplicationQueueItemEntity completedQueue = new ApplicationQueueItemEntity();
        completedQueue.setStatus("SUCCESS");
        when(queueMapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeQueue, completedQueue));

        ApplicationEntity progressed = new ApplicationEntity();
        progressed.setStatus("WRITTEN_TEST");
        ApplicationEntity terminal = new ApplicationEntity();
        terminal.setStatus("REJECTED");
        when(applicationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(progressed, terminal));

        when(logMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                log(10L, "APPLIED"), log(10L, "REPLIED"), log(10L, "REPLIED"),
                log(10L, "WRITTEN_TEST"), log(20L, "APPLIED"), log(20L, "REPLIED"),
                log(20L, "REJECTED")));

        var kpis = service.kpis(1L);

        assertThat(kpis.queueTotal()).isEqualTo(2);
        assertThat(kpis.queueActive()).isEqualTo(1);
        assertThat(kpis.applications()).isEqualTo(2);
        assertThat(kpis.applied()).isEqualTo(2);
        assertThat(kpis.replied()).isEqualTo(2);
        assertThat(kpis.writtenTest()).isEqualTo(1);
        assertThat(kpis.terminal()).isEqualTo(1);
    }

    private static ApplicationLogEntity log(Long applicationId, String toStatus) {
        ApplicationLogEntity entity = new ApplicationLogEntity();
        entity.setApplicationId(applicationId);
        entity.setToStatus(toStatus);
        return entity;
    }
}

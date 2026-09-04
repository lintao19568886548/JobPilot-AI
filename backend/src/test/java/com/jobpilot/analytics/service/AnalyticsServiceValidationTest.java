package com.jobpilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.jobpilot.analytics.mapper.AnalyticsDailyMapper;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.application.service.ApplicationService;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.dashboard.service.DashboardService;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.service.RecommendationProjectionService;
import com.jobpilot.recommendation.service.RecommendationService;
import java.time.DateTimeException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticsServiceValidationTest {
    @Test
    void rejectsReversedRebuildRangeBeforeTouchingPersistence() {
        AnalyticsService service = service(properties("Asia/Shanghai"));
        assertThatThrownBy(() -> service.rebuild(42L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("on or before");
    }

    @Test
    void rejectsRebuildRangeLongerThanOneYear() {
        AnalyticsService service = service(properties("UTC"));
        assertThatThrownBy(() -> service.rebuild(42L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("366");
    }

    @Test
    void rejectsInvalidAnalyticsTimezoneAtStartup() {
        assertThatThrownBy(() -> service(properties("Mars/Colony"))).isInstanceOf(DateTimeException.class);
    }

    private AnalyticsService service(RecommendationProperties properties) {
        return new AnalyticsService(mock(AnalyticsDailyMapper.class), mock(JobMapper.class),
                mock(JobSourceMapper.class), mock(JobRecommendationMapper.class),
                mock(RecommendationProjectionService.class), mock(RecommendationService.class),
                mock(DashboardService.class), mock(ApplicationService.class),
                mock(ApplicationQueueItemMapper.class), mock(ApplicationLogMapper.class),
                properties, mock(AuditService.class));
    }

    private RecommendationProperties properties(String timezone) {
        RecommendationProperties value = new RecommendationProperties();
        value.setAnalyticsTimezone(timezone);
        return value;
    }
}

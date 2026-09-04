package com.jobpilot.analytics.dto;

import com.jobpilot.dashboard.dto.DashboardView;
import com.jobpilot.application.dto.ApplicationDtos.ApplicationKpis;
import com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationListItem;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() { }

    public record MatchKpis(
            long totalJobs, long activeJobs, long evaluatedJobs, long unevaluatedJobs,
            long highMatchJobs, long bLevelJobs, long favoriteJobs, long ignoredJobs,
            long matchFailedJobs, long todayNewJobs, long todayEvaluatedJobs) { }

    public record MetricPoint(
            String key, String label, long value, long numerator, long denominator,
            long sampleSize, boolean insufficientSample) { }

    public record FunnelStage(
            String key, String label, long value, long numerator, long denominator,
            long sampleSize, boolean available) { }

    public record CapabilityAvailability(
            String key, boolean available, String phase, String reason) { }

    public record AnalyticsDashboardView(
            DashboardView foundation, MatchKpis matching, ApplicationKpis applications,
            List<RecommendationListItem> todayRecommendations,
            List<MetricPoint> levels, List<MetricPoint> sources,
            List<MetricPoint> recommendationStatuses,
            List<FunnelStage> funnel, List<CapabilityAvailability> unavailableCapabilities,
            String timezone, LocalDate metricDate, LocalDateTime generatedAt) { }

    public record RebuildRequest(@NotNull LocalDate from, @NotNull LocalDate to) { }

    public record RebuildResult(
            LocalDate from, LocalDate to, int dayCount, int rowCount,
            String timezone, LocalDateTime rebuiltAt) { }
}

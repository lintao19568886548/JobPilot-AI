package com.jobpilot.analytics.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class CompleteAnalyticsDtos {
    private CompleteAnalyticsDtos() { }

    public record ConversionMetric(String key, String label, long numerator, long denominator,
                                   long sampleSize, BigDecimal rate, boolean insufficientSample) { }

    public record CompleteAnalyticsView(List<ConversionMetric> funnel,
                                        Map<String, List<ConversionMetric>> dimensions,
                                        OffsetDateTime generatedAt, String timezone) { }

    public record SnapshotRequest(@NotNull LocalDate from, @NotNull LocalDate to) { }

    public record SnapshotView(String id, String schemaVersion, LocalDate from, LocalDate to,
                               String inputHash, int rowCount, CompleteAnalyticsView result,
                               OffsetDateTime generatedAt) { }
}

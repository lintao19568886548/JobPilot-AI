package com.jobpilot.operations.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OperationsDtos {
    private OperationsDtos() { }

    public record BudgetRequest(
            @NotNull @Min(0) Integer version,
            @Min(1) Long dailyTokenLimit,
            @Min(1) Long monthlyTokenLimit,
            @DecimalMin("0.000001") BigDecimal dailyCostLimit,
            @DecimalMin("0.000001") BigDecimal monthlyCostLimit,
            @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
            @NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal warningThresholdPercent,
            @NotNull Boolean enabled) { }

    public record BudgetView(String id, int version, Long dailyTokenLimit, Long monthlyTokenLimit,
                             BigDecimal dailyCostLimit, BigDecimal monthlyCostLimit, String currency,
                             BigDecimal warningThresholdPercent, boolean enabled, String status,
                             BigDecimal dailyUsagePercent, BigDecimal monthlyUsagePercent,
                             LocalDateTime updatedAt) { }

    public record RunRequest(
            @NotBlank @Size(max=32) String runType,
            @NotBlank @Size(max=24) String status,
            @NotBlank @Size(max=80) String batchId,
            @Size(max=16) String scope,
            @NotNull LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Map<String,Object> metrics,
            @NotBlank @Size(max=1000) String summary,
            @Size(max=500) String artifactManifestPath,
            @Pattern(regexp="[0-9a-f]{64}") String artifactSha256,
            @Min(0) Long rpoSeconds,
            @Min(0) Long rtoSeconds,
            @Size(max=80) String errorCode,
            @Pattern(regexp="USER|SCRIPT") String createdBy) { }

    public record RunView(String id, int version, String scope, String runType, String status,
                          String batchId, LocalDateTime startedAt, LocalDateTime finishedAt,
                          JsonNode metrics, String summary, String artifactManifestPath,
                          String artifactSha256, Long rpoSeconds, Long rtoSeconds,
                          String errorCode, String traceId, String createdBy, LocalDateTime createdAt) { }

    public record CostView(String currency, BigDecimal today, BigDecimal month) { }
    public record ProviderUsageView(String provider, String model, long calls, long inputTokens,
                                    long outputTokens, long failures) { }
    public record ProviderStatusView(String serviceStatus, boolean llmConfigured, String parserMode,
                                     String circuitBreaker, String embeddingProvider,
                                     String embeddingModel, String milvusCollection) { }
    public record UsageView(long callsToday, long callsMonth, long inputTokensToday,
                            long outputTokensToday, long inputTokensMonth, long outputTokensMonth,
                            Map<String,Long> statusCounts, List<CostView> costs,
                            List<ProviderUsageView> providers, ProviderStatusView providerStatus,
                            BudgetView budget) { }
    public record OverviewView(Map<String,String> health, Map<String,Object> observability,
                               UsageView aiUsage, List<RunView> recentRuns,
                               Map<String,Object> safety) { }
}

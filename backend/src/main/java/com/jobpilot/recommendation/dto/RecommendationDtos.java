package com.jobpilot.recommendation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.matching.dto.MatchingDtos.JobMatchView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RecommendationDtos {
    private RecommendationDtos() { }

    public record RecommendationQuery(
            @Pattern(regexp = "ALL|TOP|UNEVALUATED|IGNORED|FAVORITE") String view,
            @Pattern(regexp = "S|A|B|C|D") String level,
            @Pattern(regexp = "PASS|DOWNGRADE|REJECT") String hardFilter,
            @Pattern(regexp = "RECOMMEND|CONSIDER|NOT_RECOMMENDED|REJECTED") String recommendation,
            @Size(max = 120) String city,
            @Size(max = 26) String companyId,
            @Size(max = 200) String companyName,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            LocalDateTime publishFrom,
            LocalDateTime publishTo,
            @Size(max = 120) String skill,
            @Size(max = 40) String sourcePlatform,
            Boolean favorite,
            @Size(max = 200) String keyword,
            @Size(max = 200) String cursor,
            @Min(1) @Max(100) Integer limit,
            @Pattern(regexp = "AI_RECOMMENDED|MATCH_DESC|PUBLISH_DESC|SALARY_DESC|COMPANY_ASC|CITY_ASC") String sort) { }

    public record SkillChip(String id, String name, String requirementType, int importance) { }

    public record RecommendationJobView(
            String id, String title, String companyId, String companyName, String industry,
            String city, BigDecimal salaryMin, BigDecimal salaryMax, String currency,
            String salaryText, String jobStatus, String parseStatus, String sourcePlatform,
            List<SkillChip> skills, LocalDateTime publishAt, LocalDateTime collectedAt,
            LocalDateTime updatedAt) { }

    public record RecommendationMatchView(
            String id, String status, String hardFilterResult, BigDecimal overallScore,
            String level, String recommendation, String reason, String llmStatus,
            String algorithmVersion, Integer configVersion, String embeddingModel,
            LocalDateTime evaluatedAt) { }

    public record RecommendationListItem(
            String id, int version, String status, boolean favorite, String ignoredReason,
            BigDecimal rankScore, JsonNode rankBasis, RecommendationJobView job,
            RecommendationMatchView match, LocalDateTime updatedAt) { }

    public record RecommendationPage(
            List<RecommendationListItem> items, String nextCursor, boolean hasMore,
            int limit, long total) { }

    public record RecommendationDetail(
            RecommendationListItem recommendation, JobMatchView matchAnalysis,
            List<RecommendationEventView> events) { }

    public record RecommendationActionRequest(@NotNull @Min(0) Integer version) { }

    public record RecommendationIgnoreRequest(
            @NotNull @Min(0) Integer version,
            @Size(max = 500) String reason) { }

    public record RecommendationEventView(
            String id, String eventType, String reason, JsonNode previousState,
            JsonNode currentState, JsonNode featureSnapshot, String traceId,
            LocalDateTime occurredAt) { }

    public record RecommendationCapabilities(
            boolean applicationTracking, String applicationPhase, String reason,
            List<String> availableViews, List<String> availableSorts) { }

    public record RecommendationRefreshRequest(
            Boolean onlyUnevaluated,
            @Size(max = 120) String city,
            @Pattern(regexp = "S|A|B|C|D") String level,
            LocalDateTime updatedAfter,
            Boolean force,
            @Min(1) @Max(1000) Integer limit,
            @Size(max = 120) String idempotencyKey) { }

    public record RecommendationRefreshItemView(
            String id, String jobId, String matchRunId, String status,
            boolean reused, String errorMessage) { }

    public record RecommendationRefreshRunView(
            String id, String status, boolean force, int totalCount, int submittedCount,
            int reusedCount, int succeededCount, int failedCount,
            List<RecommendationRefreshItemView> items, LocalDateTime startedAt,
            LocalDateTime finishedAt, String errorMessage, LocalDateTime createdAt) { }
}

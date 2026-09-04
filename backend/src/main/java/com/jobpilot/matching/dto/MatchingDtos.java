package com.jobpilot.matching.dto;

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

public final class MatchingDtos {
    private MatchingDtos() { }

    public record MatchConfigRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull Map<String, @DecimalMin("0") @DecimalMax("100") BigDecimal> weights,
            @NotNull Map<String, @DecimalMin("0") @DecimalMax("100") BigDecimal> levelThresholds,
            Map<String, @DecimalMin("0") @DecimalMax("100") BigDecimal> penalties,
            Boolean activate) { }

    public record MatchConfigView(
            String id, String name, int versionNo, Map<String, BigDecimal> weights,
            Map<String, BigDecimal> levelThresholds, Map<String, BigDecimal> penalties,
            String algorithmVersion, boolean active, LocalDateTime effectiveAt, LocalDateTime createdAt) { }

    public record HardFilterRuleRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9_\\-]+") String ruleKey,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "GRADUATION_YEAR|EDUCATION|CITY|EXPERIENCE_YEARS|JOB_TYPE|SALARY|COMPANY_BLACKLIST|JOB_KEYWORD_BLACKLIST") String ruleType,
            @NotBlank @Pattern(regexp = "EQ|NE|GTE|LTE|IN|NOT_IN|CONTAINS|RANGE") String operator,
            @NotNull JsonNode operand,
            @NotBlank @Pattern(regexp = "REJECT|DOWNGRADE|WARN") String resultAction,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal penalty,
            @NotNull @Min(-10000) @Max(10000) Integer priority,
            Boolean active) { }

    public record HardFilterRuleView(
            String id, String ruleKey, String name, String ruleType, String operator, JsonNode operand,
            String resultAction, BigDecimal penalty, int priority, boolean active, int versionNo,
            LocalDateTime createdAt) { }

    public record MatchRunRequest(
            @Size(max = 26) String resumeVersionId,
            @Size(max = 120) String idempotencyKey,
            Boolean force) { }

    public record MatchRunView(
            String id, String jobId, String resumeVersionId, String configId, String status,
            int attemptCount, int maxAttempts, String resultMatchId, String errorCode,
            String errorMessage, LocalDateTime availableAt, LocalDateTime startedAt,
            LocalDateTime finishedAt, LocalDateTime createdAt) { }

    public record HardFilterEvidenceView(
            String ruleId, String ruleKey, String ruleType, String action, JsonNode expected,
            JsonNode actual, BigDecimal penalty, String explanation) { }

    public record ScoreView(
            BigDecimal skill, BigDecimal embedding, BigDecimal llm, BigDecimal project,
            BigDecimal preference, BigDecimal company, BigDecimal penalty, BigDecimal overall) { }

    public record EvidenceItemView(
            String text, List<String> candidateEvidenceRefs, String jobEvidence, String severity) { }

    public record MatchDetailItemView(
            String id, String dimension, String itemKey, String candidateEvidenceRef,
            String jobEvidence, BigDecimal score, String decision, String explanation) { }

    public record JobMatchView(
            String id, String jobId, String candidateProfileId, String resumeVersionId,
            String configId, String runId, String hardFilterResult,
            List<HardFilterEvidenceView> hardFilterReasons, ScoreView scores, String level,
            List<EvidenceItemView> advantages, List<EvidenceItemView> gaps,
            List<EvidenceItemView> risks, String recommendation, String reason,
            String recommendedResumeVersionId, String algorithmVersion, int configVersion,
            Map<String, BigDecimal> weights, Map<String, BigDecimal> effectiveWeights,
            Map<String, BigDecimal> levelThresholds, String embeddingProvider,
            String embeddingModel, String embeddingVersion, String llmStatus,
            String promptVersion, String modelName, String status,
            List<MatchDetailItemView> details, LocalDateTime evaluatedAt) { }

    public record EmbeddingRecord(
            String entityType, Long entityId, String entityPublicId, String vectorId,
            String contentHash, String collectionName, String provider, String model,
            Integer dimension, String embeddingVersion, Boolean cacheHit) { }

    public record AiAnalysisItem(
            String text, List<String> candidateEvidenceRefs, String jobEvidence, String severity) { }

    public record AiMatchResponse(
            String schemaVersion, BigDecimal embeddingScore, JsonNode embedding,
            String llmStatus, BigDecimal llmScore, List<AiAnalysisItem> advantages,
            List<AiAnalysisItem> gaps, List<AiAnalysisItem> risks, String recommendation,
            String reason, String promptVersion, String modelName, Integer inputTokens,
            Integer outputTokens, Integer elapsedMs) { }
}

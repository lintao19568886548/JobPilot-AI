package com.jobpilot.learning.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class LearningDtos {
    private LearningDtos() { }

    public record FeedbackRebuildRequest(@NotNull LocalDate from, @NotNull LocalDate to) { }
    public record FeedbackRebuildView(String id, LocalDate from, LocalDate to, int createdCount,
                                      int reusedCount, int skippedNoHistoricalMatch,
                                      boolean leakageSafe, String status, LocalDateTime finishedAt) { }
    public record FeedbackSummary(long total, Map<String, Long> byEvent, String featureSchemaVersion,
                                  int minimumSample, boolean enoughForTraining, LocalDateTime latestOccurredAt) { }

    public record TrainRequest(@NotNull LocalDate from, @NotNull LocalDate to,
                               @Size(min=2,max=160) String name) { }
    public record TrainingRunView(String id, LocalDate from, LocalDate to, LocalDateTime cutoffAt,
                                  int minimumSample, int sampleCount, int trainCount, int validationCount,
                                  String status, boolean leakageSafe, JsonNode metrics, JsonNode parameters,
                                  String errorMessage, LocalDateTime finishedAt) { }
    public record TrainResult(TrainingRunView run, ModelView model) { }
    public record ShadowResultView(String id, String recommendationId, String jobId,
                                   BigDecimal currentScore, BigDecimal shadowScore, BigDecimal scoreDelta,
                                   int currentRankPosition, int shadowRankPosition) { }
    public record ModelView(String id, int versionNo, int version, String name, String modelType,
                            int sampleCount, String status, boolean activationEligible,
                            JsonNode parameters, JsonNode evaluation, LocalDateTime shadowedAt,
                            LocalDateTime activatedAt, LocalDateTime retiredAt, LocalDateTime createdAt,
                            List<ShadowResultView> shadowResults) { }
    public record VersionRequest(@NotNull Integer version) { }
    public record RollbackView(ModelView retiredModel, ModelView activeModel, String rankingMode) { }
    public record LearningDashboard(FeedbackSummary feedback, ModelView activeModel,
                                    TrainingRunView latestTraining, int modelCount, int shadowCount,
                                    String mode, List<String> safetyBoundaries) { }
}

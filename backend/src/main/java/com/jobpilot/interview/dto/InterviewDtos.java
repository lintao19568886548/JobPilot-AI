package com.jobpilot.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() { }

    public record InterviewCreateRequest(
            @Size(max = 26) String applicationId,
            @Size(max = 26) String jobId,
            @Size(max = 26) String resumeVersionId,
            @Size(max = 200) String companyName,
            @Size(max = 200) String role,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 4000) String notes) { }

    public record InterviewUpdateRequest(
            @Size(max = 200) String companyName,
            @Size(max = 200) String role,
            @Pattern(regexp = "SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED|NO_SHOW") String status,
            @Pattern(regexp = "PENDING|PASSED|FAILED|MIXED|WITHDRAWN") String result,
            @Size(max = 64) String timezone,
            @Size(max = 4000) String notes,
            @NotNull @Min(0) Integer version) { }

    public record RoundCreateRequest(
            @NotNull @Min(1) @Max(100) Integer roundNo,
            @NotBlank @Pattern(regexp = "PHONE_SCREEN|TECHNICAL|SYSTEM_DESIGN|BEHAVIORAL|HR|MANAGER|FINAL|OTHER") String roundType,
            @NotBlank @Size(max = 160) String title,
            @NotNull OffsetDateTime scheduledStartAt,
            @NotNull OffsetDateTime scheduledEndAt,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "ONLINE|ONSITE|PHONE|OTHER") String format,
            @Size(max = 1000) String meetingLink,
            @Size(max = 300) String location,
            @Size(max = 160) String interviewerName,
            @Size(max = 4000) String notes) { }

    public record RoundUpdateRequest(
            @NotNull @Min(1) @Max(100) Integer roundNo,
            @NotBlank @Pattern(regexp = "PHONE_SCREEN|TECHNICAL|SYSTEM_DESIGN|BEHAVIORAL|HR|MANAGER|FINAL|OTHER") String roundType,
            @NotBlank @Size(max = 160) String title,
            @NotNull OffsetDateTime scheduledStartAt,
            @NotNull OffsetDateTime scheduledEndAt,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "ONLINE|ONSITE|PHONE|OTHER") String format,
            @Size(max = 1000) String meetingLink,
            @Size(max = 300) String location,
            @Size(max = 160) String interviewerName,
            @NotBlank @Pattern(regexp = "PLANNED|COMPLETED|CANCELLED") String status,
            @Pattern(regexp = "PENDING|PASSED|FAILED|MIXED|WITHDRAWN") String result,
            @Size(max = 4000) String notes,
            @NotNull @Min(0) Integer version) { }

    public record QuestionCreateRequest(
            @NotBlank @Pattern(regexp = "ACTUAL|MANUAL") String sourceType,
            @NotBlank @Pattern(regexp = "TECHNICAL_FOUNDATION|PROJECT_DEEP_DIVE|SYSTEM_DESIGN|BEHAVIORAL|ROLE_RISK|FOLLOW_UP|OTHER") String category,
            @NotBlank @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
            @NotBlank @Size(max = 4000) String question,
            @Size(max = 1000) String purpose,
            @Size(max = 2000) String basis,
            @Size(max = 20000) String answerFramework,
            @Size(max = 20) List<@Size(max = 1000) String> suggestedFollowUps,
            @Size(max = 2000) String riskNotes,
            @Min(0) @Max(10000) Integer displayOrder) { }

    public record QuestionUpdateRequest(
            @NotBlank @Pattern(regexp = "TECHNICAL_FOUNDATION|PROJECT_DEEP_DIVE|SYSTEM_DESIGN|BEHAVIORAL|ROLE_RISK|FOLLOW_UP|OTHER") String category,
            @NotBlank @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
            @NotBlank @Size(max = 4000) String question,
            @Size(max = 1000) String purpose,
            @Size(max = 2000) String basis,
            @Size(max = 20000) String answerFramework,
            @Size(max = 20) List<@Size(max = 1000) String> suggestedFollowUps,
            @Size(max = 2000) String riskNotes,
            @Min(0) @Max(10000) Integer displayOrder,
            @NotNull @Min(0) Integer version) { }

    public record AnswerNoteRequest(
            @NotBlank @Size(max = 30000) String answer,
            @Min(1) @Max(5) Integer selfRating,
            OffsetDateTime recordedAt) { }

    public record ReminderRequest(
            @NotBlank @Size(max = 26) String interviewId,
            @Size(max = 26) String roundId,
            @NotBlank @Pattern(regexp = "PREPARE|START|REVIEW|CUSTOM") String reminderType,
            @NotBlank @Size(max = 240) String title,
            @NotNull OffsetDateTime remindAt,
            @NotBlank @Size(max = 64) String timezone) { }

    public record ReminderUpdateRequest(
            @NotBlank @Pattern(regexp = "PREPARE|START|REVIEW|CUSTOM") String reminderType,
            @NotBlank @Size(max = 240) String title,
            @NotNull OffsetDateTime remindAt,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @Min(0) Integer version) { }

    public record VersionRequest(@NotNull @Min(0) Integer version) { }

    public record AnswerNoteView(String id, Integer noteVersion, String answer, Integer selfRating,
                                 OffsetDateTime recordedAt, OffsetDateTime createdAt) { }
    public record QuestionView(String id, String sourceType, String category, String difficulty,
                               String question, String purpose, String basis, String answerFramework,
                               List<String> suggestedFollowUps, String riskNotes, List<String> evidenceRefs,
                               String promptVersion, String modelName, Integer displayOrder, Integer version,
                               OffsetDateTime generatedAt, OffsetDateTime createdAt, List<AnswerNoteView> answerNotes) { }
    public record RoundView(String id, Integer roundNo, String roundType, String title,
                            OffsetDateTime scheduledStartAt, OffsetDateTime scheduledEndAt, String timezone,
                            String format, String meetingLink, String location, String interviewerName,
                            String status, String result, String notes, Integer version,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<QuestionView> questions) { }
    public record InterviewView(String id, String applicationId, String jobId, String resumeVersionId,
                                String companyName, String role, String status, String result, String timezone,
                                String notes, Integer version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                List<RoundView> rounds) { }
    public record InterviewPage(List<InterviewView> items, long total, int page, int size) { }

    public record ReviewItemView(String id, String itemType, String title, String description,
                                 String severity, String evidence, List<String> evidenceRefs,
                                 List<String> recommendedActions, Integer displayOrder) { }
    public record ReviewView(String id, Integer reviewVersion, String status, String summary,
                             String sourceType, String promptVersion, String modelName,
                             List<String> evidenceRefs, OffsetDateTime generatedAt,
                             OffsetDateTime confirmedAt, Integer version, List<ReviewItemView> items) { }
    public record KnowledgeGapView(String id, String sourceInterviewId, String sourceReviewId,
                                   String title, String category, String description, String severity,
                                   JsonNode evidence, List<String> recommendedActions, String status,
                                   OffsetDateTime confirmedAt, OffsetDateTime resolvedAt,
                                   OffsetDateTime dismissedAt, Integer version, OffsetDateTime createdAt) { }
    public record ReminderView(String id, String interviewId, String roundId, String reminderType,
                               String title, OffsetDateTime remindAt, String timezone, String status,
                               OffsetDateTime completedAt, OffsetDateTime cancelledAt, Integer version,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) { }

    public record InterviewDashboardView(List<InterviewView> upcomingInterviews,
                                         List<ReminderView> pendingReminders,
                                         long reviewPendingCount, long activeKnowledgeGapCount) { }

    public record AiEvidence(String evidenceRef, String evidenceType, String text) { }
    public record AiQuestion(String sourceType, String category, String difficulty, String question,
                             String purpose, String basis, String answerFramework,
                             List<String> evidenceRefs, List<String> suggestedFollowUps, String riskNotes) { }
    public record AiPredictionResponse(String schemaVersion, String status, String executionMode,
                                       String promptVersion, String modelName, List<AiQuestion> questions,
                                       List<String> gaps, Integer elapsedMs) { }
    public record AiActualAnswer(String questionRef, String question, String answer, Integer selfRating) { }
    public record AiReviewItem(String itemType, String title, String description, String severity,
                               String evidence, List<String> evidenceRefs, List<String> recommendedActions) { }
    public record AiReviewResponse(String schemaVersion, String status, String executionMode,
                                   String promptVersion, String modelName, String summary,
                                   List<String> evidenceRefs, List<AiReviewItem> items, Integer elapsedMs) { }
    public record AiResult<T>(T response, String requestHash, String responseHash) { }
}

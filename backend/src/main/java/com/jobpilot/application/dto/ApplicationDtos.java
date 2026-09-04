package com.jobpilot.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApplicationDtos {
    private ApplicationDtos() { }

    public record PolicyDecisionView(
            String platform, String collectionMode, String applicationMode,
            boolean requiresFinalConfirmation, boolean configured, boolean effective,
            String reason, LocalDateTime reviewedAt) { }

    public record PolicyUpdateRequest(
            @NotBlank @Pattern(regexp = "MANUAL_ONLY|VISIBLE_PAGE_ONLY") String collectionMode,
            @NotBlank @Pattern(regexp = "MANUAL_ONLY|ASSIST_ALLOWED") String applicationMode,
            @NotNull Boolean requiresFinalConfirmation,
            @Size(max = 1000) String policySourceUrl) { }

    public record QueueEnqueueRequest(
            @NotBlank @Size(max = 26) String jobId,
            @Size(max = 26) String jobMatchId,
            @Size(max = 26) String resumeVersionId,
            @Size(max = 160) String greetingReference,
            @NotBlank @Pattern(regexp = "MANUAL|ASSIST|AUTHORIZED_AUTOMATION") String mode,
            @Min(0) @Max(100) Integer priority,
            LocalDateTime scheduledAt,
            Boolean allowUnevaluated) { }

    public record QueueBatchRequest(@NotEmpty @Size(max = 100) List<@Valid QueueEnqueueRequest> items) { }

    public record QueueUpdateRequest(
            @Size(max = 26) String resumeVersionId,
            @Size(max = 160) String greetingReference,
            @Min(0) @Max(100) Integer priority,
            LocalDateTime scheduledAt,
            @NotNull @Min(0) Integer version) { }

    public record VersionRequest(@NotNull @Min(0) Integer version) { }

    public record QueueJobView(
            String id, String title, String companyName, String city,
            String platform, String sourceUrl, String status) { }

    public record QueueResumeView(String versionId, String resumeId, String resumeName,
                                  Integer versionNumber, String versionName) { }

    public record QueueItemView(
            String id, QueueJobView job, String jobMatchId, QueueResumeView resume,
            String greetingReference, String mode, String status, Integer priority,
            LocalDateTime scheduledAt, LocalDateTime approvedAt, LocalDateTime preparedAt,
            String lastErrorCode, String lastErrorMessage, int version,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            PolicyDecisionView policy) { }

    public record QueueBatchResult(List<QueueItemView> items, int requestedCount,
                                   int uniqueResultCount, int reusedCount) { }

    public record AssistPrepareView(
            QueueItemView item, boolean externallySubmitted, boolean applicationCreated,
            boolean finalConfirmationRequired, String nextAction, List<String> safetyBoundaries) { }

    public record ApplicationCreateRequest(
            @Size(max = 26) String queueItemId,
            @Size(max = 26) String jobId,
            @Size(max = 26) String resumeVersionId,
            @Size(max = 26) String recruiterId,
            @NotBlank @Pattern(regexp = "MANUAL|ASSIST") String mode,
            @NotNull Boolean confirmedExternalSubmission,
            LocalDateTime appliedAt,
            @Size(max = 200) String externalApplicationId,
            @Size(max = 1000) String sourceUrlSnapshot,
            @Size(max = 4000) String notes) { }

    public record ApplicationUpdateRequest(
            @Size(max = 26) String recruiterId,
            @Size(max = 4000) String notes,
            @NotNull @Min(0) Integer version) { }

    public record TransitionRequest(
            @NotBlank @Pattern(regexp = "APPLIED|VIEWED|REPLIED|WRITTEN_TEST|INTERVIEW_1|INTERVIEW_2|INTERVIEW_3|HR_INTERVIEW|OFFER|REJECTED|WITHDRAWN|CLOSED") String toStatus,
            LocalDateTime occurredAt,
            @NotBlank @Pattern(regexp = "USER|PLATFORM_IMPORT|SYSTEM") String source,
            @Size(max = 2000) String note,
            JsonNode evidence,
            @NotNull @Min(0) Integer version) { }

    public record ApplicationLogView(
            String id, String fromStatus, String toStatus, String eventType,
            LocalDateTime occurredAt, String source, String note,
            JsonNode evidence, String traceId, LocalDateTime createdAt) { }

    public record ApplicationView(
            String id, QueueJobView job, QueueResumeView resume, String recruiterId,
            String queueItemId, String status, String applicationMode,
            LocalDateTime appliedAt, LocalDateTime lastStatusAt,
            String externalApplicationId, String sourceUrlSnapshot, String notes,
            int version, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<ApplicationLogView> timeline, List<String> allowedTransitions) { }

    public record ApplicationPage(List<ApplicationView> items, long total, Map<String, Long> statusCounts) { }

    public record RecruiterRequest(
            @Size(max = 26) String companyId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 160) String position,
            @Size(max = 80) String platform,
            @Size(max = 160) String platformRecruiterId,
            @Size(max = 160) String contactMasked,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[+0-9()\\- ]{6,40}$") String phone,
            LocalDateTime nextFollowUpAt,
            @Pattern(regexp = "NEW|CONTACTED|REPLIED|FOLLOW_UP|CLOSED") String communicationStatus,
            @Size(max = 2000) String notes) { }

    public record RecruiterPatchRequest(
            @Size(max = 160) String position,
            @Size(max = 160) String contactMasked,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[+0-9()\\- ]{6,40}$") String phone,
            LocalDateTime nextFollowUpAt,
            @Pattern(regexp = "NEW|CONTACTED|REPLIED|FOLLOW_UP|CLOSED") String communicationStatus,
            @Size(max = 2000) String notes,
            @NotNull @Min(0) Integer version) { }

    public record InteractionRequest(
            @Size(max = 26) String applicationId,
            @NotBlank @Pattern(regexp = "PLATFORM|EMAIL|PHONE|WECHAT|SMS|OTHER") String channel,
            @NotBlank @Pattern(regexp = "OUTBOUND|INBOUND|NOTE") String direction,
            LocalDateTime occurredAt,
            @NotBlank @Size(max = 2000) String summary,
            LocalDateTime followUpAt) { }

    public record InteractionView(
            String id, String applicationId, String channel, String direction,
            LocalDateTime occurredAt, String summary, LocalDateTime followUpAt,
            LocalDateTime createdAt) { }

    public record RecruiterView(
            String id, String companyId, String companyName, String name, String position,
            String platform, String platformRecruiterId, String contactMasked,
            String email, String phone, LocalDateTime lastContactAt,
            LocalDateTime nextFollowUpAt, String communicationStatus, String notes,
            int version, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<InteractionView> interactions) { }

    public record ApplicationKpis(
            long queueTotal, long queueActive, long queueNeedReview, long applications,
            long applied, long viewed, long replied, long writtenTest,
            long interviewStage, long offerStage, long terminal) { }
}

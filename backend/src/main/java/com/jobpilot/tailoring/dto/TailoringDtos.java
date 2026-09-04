package com.jobpilot.tailoring.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class TailoringDtos {
    private TailoringDtos() { }

    public record EvidenceView(String evidenceRef, String evidenceKey, String evidenceType,
                               String sourceType, String sourceId, String fieldPath,
                               JsonNode value, String text, String contentHash,
                               Integer versionNumber, LocalDateTime createdAt) { }
    public record EvidenceLedgerView(List<EvidenceView> items, int count, String snapshotHash,
                                     LocalDateTime refreshedAt) { }

    public record TailorCreateRequest(
            @Size(max = 26) String baseResumeVersionId,
            @Size(max = 26) String targetResumeId,
            @Pattern(regexp = "BALANCED|ATS|CONCISE") String strategy) { }
    public record TailorApproveRequest(@Size(max = 160) String versionName) { }
    public record TailorChangeView(String id, String sectionType, String operation,
                                   String before, String after, String reason,
                                   List<String> evidenceRefs, Integer sortOrder) { }
    public record TailorRunView(String id, String jobId, String jobTitle, String companyName,
                                String baseResumeVersionId, String targetResumeId,
                                String approvedResumeVersionId, String strategy, String status,
                                String executionMode, String promptVersion, String modelName,
                                String truthCheckStatus, JsonNode proposedContent,
                                String proposedRenderedText, List<TailorChangeView> changes,
                                LocalDateTime startedAt, LocalDateTime completedAt,
                                LocalDateTime approvedAt, int version, LocalDateTime createdAt) { }

    public record DraftCreateRequest(
            @NotBlank @Pattern(regexp = "BOSS|LIEPIN|EMAIL|WECHAT|THANK_YOU|FOLLOW_UP|OFFER") String channel,
            @NotBlank @Size(max = 40) String purpose,
            @Size(max = 26) String applicationId,
            @Size(max = 26) String resumeVersionId,
            @Size(max = 26) String recruiterId) { }
    public record VersionRequest(@NotNull @Min(0) Integer version) { }
    public record CommunicationDraftView(String id, String jobId, String jobTitle, String companyName,
                                         String applicationId, String resumeVersionId, String recruiterId,
                                         String channel, String purpose, String content, Integer charCount,
                                         List<String> evidenceRefs, String promptVersion,
                                         String executionMode, String truthCheckStatus, String status,
                                         boolean externallySent, LocalDateTime approvedAt,
                                         LocalDateTime usedAt, int version, LocalDateTime createdAt,
                                         LocalDateTime updatedAt) { }

    public record ResumeVersionMetricView(String resumeVersionId, int queueUseCount,
                                          int applicationCount, int replyCount,
                                          int interviewCount, int offerCount,
                                          LocalDateTime calculatedAt) { }

    public record AiEvidence(String evidenceRef, String evidenceType, String text) { }
    public record AiSection(String sectionType, JsonNode content, Integer sortOrder) { }
    public record AiBaseResume(String versionId, JsonNode content, String renderedText, List<AiSection> sections) { }
    public record AiTailorChange(String sectionType, String operation, String before,
                                 String after, String reason, List<String> evidenceRefs) { }
    public record AiTailorResponse(String schemaVersion, String status, String executionMode,
                                   String promptVersion, String modelName, String truthCheckStatus,
                                   List<AiTailorChange> changes, JsonNode proposedContent,
                                   String proposedRenderedText, Integer elapsedMs) { }
    public record AiDraftResponse(String schemaVersion, String status, String executionMode,
                                  String promptVersion, String modelName, String truthCheckStatus,
                                  String content, Integer charCount, List<String> evidenceRefs,
                                  Boolean externallySent, Integer elapsedMs) { }
    public record AiResult<T>(T response, String requestHash, String responseHash) { }
}

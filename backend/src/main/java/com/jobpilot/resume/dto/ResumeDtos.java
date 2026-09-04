package com.jobpilot.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.resume.domain.ResumeSectionType;
import com.jobpilot.resume.domain.ResumeSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record ResumeCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String targetRole,
            Boolean master,
            Boolean defaultResume,
            @Size(max = 2000) String description,
            @Size(max = 24) String status) {
    }

    public record ResumeUpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String targetRole,
            @Size(max = 2000) String description,
            @Size(max = 24) String status) {
    }

    public record ResumeSummaryView(
            String id,
            String name,
            String targetRole,
            Boolean master,
            Boolean defaultResume,
            String description,
            String status,
            String currentVersionId,
            Integer currentVersionNumber,
            long versionCount,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ResumeDetailView(ResumeSummaryView resume, List<ResumeVersionSummaryView> versions) {
    }

    public record SectionRequest(
            @NotNull ResumeSectionType sectionType,
            @NotNull Object content,
            @Min(0) @Max(10000) Integer sortOrder) {
    }

    public record VersionCreateRequest(
            @Size(max = 160) String versionName,
            @NotNull Object content,
            @Size(max = 20000) String renderedText,
            @NotNull ResumeSourceType sourceType,
            @Size(max = 40) String createdBy,
            @Valid @Size(max = 30) List<SectionRequest> sections) {
    }

    public record ResumeSectionView(
            String id, ResumeSectionType sectionType, JsonNode content, Integer sortOrder) {
    }

    public record ResumeVersionSummaryView(
            String id,
            Integer versionNumber,
            String versionName,
            ResumeSourceType sourceType,
            String createdBy,
            Boolean active,
            Boolean current,
            LocalDateTime createdAt,
            String parentVersionId,
            String tailoredForJobId,
            String truthCheckStatus) {
    }

    public record ResumeVersionView(
            String id,
            String resumeId,
            Integer versionNumber,
            String versionName,
            JsonNode content,
            String renderedText,
            ResumeSourceType sourceType,
            String createdBy,
            Boolean active,
            Boolean current,
            String contentHash,
            LocalDateTime createdAt,
            List<ResumeSectionView> sections,
            String parentVersionId,
            String tailoredForJobId,
            String promptVersion,
            String modelName,
            String truthCheckStatus) {
    }

    public record TailoredVersionCommand(
            String baseVersionId, String jobId, Long promptTemplateId, Long aiCallId,
            String promptVersion, String modelName, String versionName, JsonNode content,
            String renderedText, List<SectionRequest> sections) { }
}

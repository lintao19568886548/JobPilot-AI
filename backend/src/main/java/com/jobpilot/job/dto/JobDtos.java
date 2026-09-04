package com.jobpilot.job.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class JobDtos {
    private JobDtos() { }

    public record CompanyRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 500) @Pattern(regexp = "^$|https?://.*", message = "website must be HTTP(S)") String website,
            @Size(max = 120) String industry,
            @Size(max = 40) String companySize,
            @Size(max = 40) String financingStage,
            @Size(max = 100) String headquartersCity,
            @Size(max = 5000) String description,
            @Size(max = 80) String verifiedSource) { }

    public record CompanyView(
            String id, String normalizedName, String displayName, String website, String industry,
            String companySize, String financingStage, String headquartersCity, String description,
            String verifiedSource, JsonNode riskFlags, int version) { }

    public record CompanyUpdateRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 500) @Pattern(regexp = "^$|https?://.*") String website,
            @Size(max = 120) String industry,
            @Size(max = 40) String companySize,
            @Size(max = 40) String financingStage,
            @Size(max = 100) String headquartersCity,
            @Size(max = 5000) String description,
            @Size(max = 80) String verifiedSource,
            @Min(0) int version) { }

    public record JobCreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String companyName,
            @Size(max = 500) @Pattern(regexp = "^$|https?://.*", message = "companyWebsite must be HTTP(S)") String companyWebsite,
            @Size(max = 120) String industry,
            @Size(max = 40) String companySize,
            @Size(max = 40) String financingStage,
            @Size(max = 100) String headquartersCity,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 300) String workplaceText,
            @Pattern(regexp = "ONSITE|HYBRID|REMOTE|UNKNOWN") String remoteType,
            @DecimalMin("0") BigDecimal salaryMin,
            @DecimalMin("0") BigDecimal salaryMax,
            @Min(1) @Max(24) Integer salaryMonths,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @Size(max = 120) String salaryText,
            @Size(max = 40) String education,
            @DecimalMin("0") @DecimalMax("80") BigDecimal experienceMinYears,
            @DecimalMin("0") @DecimalMax("80") BigDecimal experienceMaxYears,
            @Min(1950) @Max(2200) Integer graduateYear,
            @Pattern(regexp = "FULL_TIME|INTERNSHIP|PART_TIME|CONTRACT|OTHER") String jobType,
            @NotBlank @Size(max = 100000) String description,
            @Size(max = 160) String businessDomain,
            @Size(max = 160) String teamName,
            LocalDateTime publishAt,
            @Size(max = 40) String platform,
            @Size(max = 160) String platformJobId,
            @Pattern(regexp = "MANUAL|URL|EXTENSION|CSV|EXCEL") String sourceType,
            @Size(max = 1000) @Pattern(regexp = "^$|https?://.*", message = "jobUrl must be HTTP(S)") String jobUrl,
            Boolean userInitiated,
            Boolean parseAfterCreate) { }

    public record JobUpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String companyName,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 300) String workplaceText,
            @Pattern(regexp = "ONSITE|HYBRID|REMOTE|UNKNOWN") String remoteType,
            @DecimalMin("0") BigDecimal salaryMin,
            @DecimalMin("0") BigDecimal salaryMax,
            @Min(1) @Max(24) Integer salaryMonths,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @Size(max = 120) String salaryText,
            @Size(max = 40) String education,
            @DecimalMin("0") @DecimalMax("80") BigDecimal experienceMinYears,
            @DecimalMin("0") @DecimalMax("80") BigDecimal experienceMaxYears,
            @Min(1950) @Max(2200) Integer graduateYear,
            @Pattern(regexp = "FULL_TIME|INTERNSHIP|PART_TIME|CONTRACT|OTHER") String jobType,
            @NotBlank @Size(max = 100000) String description,
            @Size(max = 160) String businessDomain,
            @Size(max = 160) String teamName,
            LocalDateTime publishAt,
            @Size(max = 500) String reason,
            @Min(0) int version) { }

    public record SourceRequest(
            @NotBlank @Size(max = 40) String platform,
            @Size(max = 160) String platformJobId,
            @NotBlank @Pattern(regexp = "MANUAL|URL|EXTENSION|CSV|EXCEL") String sourceType,
            @Size(max = 1000) @Pattern(regexp = "^$|https?://.*") String jobUrl,
            @Size(max = 200) String sourceTitle,
            @Size(max = 200) String sourceCompanyName,
            JsonNode rawSnapshot,
            LocalDateTime publishAt,
            @Size(max = 80) String collectorVersion,
            Boolean userInitiated) { }

    public record SourceView(
            String id, String platform, String platformJobId, String sourceType, String jobUrl,
            String sourceTitle, String sourceCompanyName, JsonNode rawSnapshot, LocalDateTime publishAt,
            LocalDateTime collectedAt, LocalDateTime lastSeenAt, String availabilityStatus,
            String collectorVersion, boolean userInitiated) { }

    public record JobSkillView(
            String id, String skillId, String canonicalName, String displayName, String requirementType,
            int importance, BigDecimal minYears, String evidenceText, String source, BigDecimal confidence) { }

    public record ParseRunView(
            String id, String status, String parserMode, String parserVersion, String schemaVersion,
            String promptVersion, String modelName, String inputHash, String outputHash, JsonNode result,
            String errorCode, String errorMessage, Integer elapsedMs, LocalDateTime createdAt) { }

    public record JobSummaryView(
            String id, String title, String normalizedTitle, CompanyView company, String city, String district,
            String workplaceText, String remoteType, BigDecimal salaryMin, BigDecimal salaryMax,
            Integer salaryMonths, String currency, String salaryText, String education,
            BigDecimal experienceMinYears, BigDecimal experienceMaxYears, Integer graduateYear,
            String jobType, String status, String parseStatus, String parserMode, String parserVersion, LocalDateTime publishAt,
            LocalDateTime updatedAt, int sourceCount, int skillCount, int version) { }

    public record JobDetailView(
            JobSummaryView job, String descriptionRaw, String descriptionClean, List<String> responsibilities,
            List<String> requirements, String businessDomain, String teamName, String rawContentHash,
            JsonNode parseResult, List<String> manualFields, List<SourceView> sources,
            List<JobSkillView> skills, List<ParseRunView> parseRuns) { }

    public record JobPageView(List<JobSummaryView> items, String nextCursor, boolean hasMore, int limit) { }

    public record JobQuery(
            String cursor, Integer limit, String title, String city, BigDecimal salaryMin,
            BigDecimal salaryMax, String education, BigDecimal experienceMin, BigDecimal experienceMax,
            String company, String industry, String platform, String skill, String companySize,
            String status, String parseStatus, String sort, LocalDateTime publishFrom, LocalDateTime publishTo) { }

    public record CreateResult(JobDetailView job, String dedupDecision, boolean created) { }

    public record UrlImportRequest(
            @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://.*") String url,
            @Size(max = 40) String platform,
            @Size(max = 160) String platformJobId,
            Boolean userInitiated,
            @NotBlank @Size(max = 120) String idempotencyKey) { }

    public record ImportTaskView(
            String id, String importType, String sourceUrl, String fileName, String fileHash,
            String idempotencyKey, String status, int totalCount, int successCount, int failureCount,
            JsonNode errorSummary, LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record ImportErrorView(
            String id, Integer rowNumber, String errorCode, String errorMessage,
            String fieldName, String rawPreview, LocalDateTime createdAt) { }

    public record ExtensionCaptureRequest(
            @NotBlank @Size(max = 40) String platform,
            @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://.*") String pageUrl,
            LocalDateTime capturedAt,
            Boolean userInitiated,
            @NotBlank @Size(max = 80) String adapterVersion,
            Map<String, Object> visibleFields,
            @NotBlank @Size(max = 80) String contentHash) { }
}

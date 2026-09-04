package com.jobpilot.offer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class OfferDtos {
    private OfferDtos() { }

    public record BenefitRequest(
            @NotBlank @Pattern(regexp = "CASH|INSURANCE|LEAVE|HOUSING|MEAL|TRANSPORT|LEARNING|HEALTH|EQUITY|OTHER") String benefitType,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String value,
            @DecimalMin("0.00") BigDecimal quantifiedValue,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @Min(0) @Max(1000) Integer displayOrder) { }

    public record OfferCreateRequest(
            @NotBlank String applicationId,
            @NotNull @DecimalMin("0.00") BigDecimal baseSalary,
            @NotBlank @Pattern(regexp = "MONTHLY|ANNUAL") String salaryPeriod,
            @NotNull @DecimalMin("0.01") @DecimalMax("36.00") BigDecimal salaryMonths,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin("0.00") BigDecimal guaranteedBonus,
            @DecimalMin("0.00") BigDecimal variableBonusMin,
            @DecimalMin("0.00") BigDecimal variableBonusMax,
            @Size(max = 1000) String equity,
            @Size(max = 1000) String housing,
            @Size(max = 1000) String workTime,
            @Size(max = 1000) String overtime,
            @Size(max = 240) String location,
            @Pattern(regexp = "ONSITE|HYBRID|REMOTE|UNKNOWN") String remoteType,
            @Min(0) @Max(24) Integer probationMonths,
            @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal probationSalaryRatio,
            LocalDate startDate,
            OffsetDateTime deadlineAt,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 4000) String notes,
            @Valid @Size(max = 50) List<BenefitRequest> benefits) { }

    public record OfferUpdateRequest(
            @NotNull @DecimalMin("0.00") BigDecimal baseSalary,
            @NotBlank @Pattern(regexp = "MONTHLY|ANNUAL") String salaryPeriod,
            @NotNull @DecimalMin("0.01") @DecimalMax("36.00") BigDecimal salaryMonths,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin("0.00") BigDecimal guaranteedBonus,
            @DecimalMin("0.00") BigDecimal variableBonusMin,
            @DecimalMin("0.00") BigDecimal variableBonusMax,
            @Size(max = 1000) String equity,
            @Size(max = 1000) String housing,
            @Size(max = 1000) String workTime,
            @Size(max = 1000) String overtime,
            @Size(max = 240) String location,
            @Pattern(regexp = "ONSITE|HYBRID|REMOTE|UNKNOWN") String remoteType,
            @Min(0) @Max(24) Integer probationMonths,
            @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal probationSalaryRatio,
            LocalDate startDate,
            OffsetDateTime deadlineAt,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 4000) String notes,
            @Valid @Size(max = 50) List<BenefitRequest> benefits,
            @NotNull @Min(0) Integer version) { }

    public record OfferStatusRequest(
            @NotBlank @Pattern(regexp = "DRAFT|RECEIVED|CONSIDERING|ACCEPTED|DECLINED|EXPIRED|WITHDRAWN") String status,
            @NotNull @Min(0) Integer version) { }

    public record DeadlineRequest(
            @NotBlank String offerId,
            @NotBlank @Pattern(regexp = "DECISION|START|DOCUMENT|CUSTOM") String deadlineType,
            @NotBlank @Size(max = 240) String title,
            @NotNull OffsetDateTime dueAt,
            @NotBlank @Size(max = 64) String timezone) { }

    public record DeadlineUpdateRequest(
            @NotBlank @Pattern(regexp = "DECISION|START|DOCUMENT|CUSTOM") String deadlineType,
            @NotBlank @Size(max = 240) String title,
            @NotNull OffsetDateTime dueAt,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @Min(0) Integer version) { }

    public record VersionRequest(@NotNull @Min(0) Integer version) { }

    public record BenefitView(String id, String benefitType, String name, String value,
                              BigDecimal quantifiedValue, String currency, Integer displayOrder, Integer version) { }

    public record OfferView(String id, String applicationId, String jobId, String resumeVersionId,
                            String companyName, String role, String status, BigDecimal baseSalary,
                            String salaryPeriod, BigDecimal salaryMonths, String currency,
                            BigDecimal guaranteedBonus, BigDecimal variableBonusMin, BigDecimal variableBonusMax,
                            BigDecimal guaranteedAnnualCash, BigDecimal potentialAnnualCash,
                            BigDecimal probationMonthlyCash, String equity, String housing, String workTime,
                            String overtime, String location, String remoteType, Integer probationMonths,
                            BigDecimal probationSalaryRatio, LocalDate startDate, OffsetDateTime deadlineAt,
                            String timezone, String notes, List<BenefitView> benefits, Integer version,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) { }

    public record OfferPage(List<OfferView> items, long total, int page, int size) { }

    public record DeadlineView(String id, String offerId, String companyName, String role,
                               String deadlineType, String title, OffsetDateTime dueAt, String timezone,
                               String status, OffsetDateTime completedAt, OffsetDateTime cancelledAt,
                               Integer version) { }

    public record ComparisonRequest(@NotBlank @Size(max = 200) String name,
                                    @NotEmpty @Size(min = 2, max = 10) List<String> offerIds,
                                    @NotNull Map<String, BigDecimal> weights,
                                    Map<String, Map<String, BigDecimal>> ratings) { }

    public record DimensionView(String key, BigDecimal rawValue, BigDecimal normalizedScore,
                                BigDecimal weight, BigDecimal contribution, boolean unknown, String explanation) { }

    public record ComparisonItemView(String id, String offerId, String companyName, String role, int rank,
                                     BigDecimal guaranteedAnnualCash, BigDecimal potentialAnnualCash,
                                     BigDecimal overallScore, List<DimensionView> dimensions, String explanation) { }

    public record ComparisonView(String id, int comparisonVersion, String name, List<String> offerIds,
                                 Map<String, BigDecimal> weights, String currencyGroup, boolean cashComparable,
                                 String summary, List<ComparisonItemView> items, OffsetDateTime createdAt) { }

    public record OfferDashboardView(long activeOffers, long acceptedOffers,
                                     List<DeadlineView> pendingDeadlines, OffsetDateTime generatedAt) { }
}

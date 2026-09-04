package com.jobpilot.candidate.dto;

import com.jobpilot.candidate.domain.EmploymentType;
import com.jobpilot.candidate.domain.SkillCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CandidateDtos {

    private static final String URL_PATTERN = "^(https?://.+)?$";

    private CandidateDtos() {
    }

    public record ProfileRequest(
            @Size(max = 100) String fullName,
            @Size(max = 160) String headline,
            @Pattern(regexp = "^[+0-9() -]{0,32}$", message = "invalid phone format") String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 100) String currentCity,
            @Size(max = 20) List<@Size(max = 100) String> targetCities,
            @Min(1950) @Max(2100) Integer graduationYear,
            @Size(max = 40) String highestEducation,
            @Size(max = 160) String school,
            @Size(max = 160) String major,
            @DecimalMin("0.0") @DecimalMax("80.0") BigDecimal yearsOfExperience,
            @Size(max = 40) String jobStatus,
            @Pattern(regexp = URL_PATTERN, message = "invalid URL") @Size(max = 500) String githubUrl,
            @Pattern(regexp = URL_PATTERN, message = "invalid URL") @Size(max = 500) String personalWebsite,
            @Size(max = 4000) String summary,
            @Size(max = 20) List<@Size(max = 120) String> targetRoles,
            @Size(max = 20) List<@Size(max = 120) String> targetIndustries,
            @Size(max = 20) List<@Size(max = 120) String> targetCompanyTypes,
            @DecimalMin("0") @DecimalMax("10000000") BigDecimal targetSalaryMin,
            @DecimalMin("0") @DecimalMax("10000000") BigDecimal targetSalaryMax,
            @Pattern(regexp = "^[A-Z]{3}$") String salaryCurrency,
            Boolean acceptRemote,
            Boolean acceptRelocation) {
    }

    public record ProfileView(
            String id,
            String fullName,
            String headline,
            String phone,
            String email,
            String currentCity,
            List<String> targetCities,
            Integer graduationYear,
            String highestEducation,
            String school,
            String major,
            BigDecimal yearsOfExperience,
            String jobStatus,
            String githubUrl,
            String personalWebsite,
            String summary,
            List<String> targetRoles,
            List<String> targetIndustries,
            List<String> targetCompanyTypes,
            BigDecimal targetSalaryMin,
            BigDecimal targetSalaryMax,
            String salaryCurrency,
            Boolean acceptRemote,
            Boolean acceptRelocation,
            int profileCompleteness,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CompletenessView(int score, Map<String, Integer> breakdown) {
    }

    public record EducationRequest(
            @NotBlank @Size(max = 160) String school,
            @NotBlank @Size(max = 40) String degree,
            @NotBlank @Size(max = 160) String major,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Min(1950) @Max(2100) Integer graduationYear,
            @Size(max = 4000) String description,
            @Min(0) @Max(10000) Integer sortOrder) {
    }

    public record EducationView(
            String id, String school, String degree, String major,
            LocalDate startDate, LocalDate endDate, Integer graduationYear,
            String description, Integer sortOrder, int version) {
    }

    public record ExperienceRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 160) String role,
            @NotNull EmploymentType employmentType,
            @Size(max = 160) String location,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean currentlyWorking,
            @Size(max = 4000) String description,
            @Size(max = 8000) String responsibilities,
            @Size(max = 8000) String achievements,
            @Size(max = 50) List<@Size(max = 100) String> technologies,
            @Min(0) @Max(10000) Integer sortOrder) {
    }

    public record ExperienceView(
            String id, String companyName, String role, EmploymentType employmentType,
            String location, LocalDate startDate, LocalDate endDate, Boolean currentlyWorking,
            String description, String responsibilities, String achievements,
            List<String> technologies, Integer sortOrder, int version) {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 160) String role,
            LocalDate startDate,
            LocalDate endDate,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 4000) String background,
            @Size(max = 8000) String responsibilities,
            @Size(max = 8000) String achievements,
            @Size(max = 50) List<@Size(max = 100) String> technologies,
            @Pattern(regexp = URL_PATTERN, message = "invalid URL") @Size(max = 500) String repoUrl,
            @Pattern(regexp = URL_PATTERN, message = "invalid URL") @Size(max = 500) String demoUrl,
            Boolean featured,
            @Min(0) @Max(10000) Integer sortOrder) {
    }

    public record ProjectView(
            String id, String name, String role, LocalDate startDate, LocalDate endDate,
            String description, String background, String responsibilities, String achievements,
            List<String> technologies, String repoUrl, String demoUrl,
            Boolean featured, Integer sortOrder, int version) {
    }

    public record SkillView(
            String id, String canonicalName, String displayName,
            SkillCategory category, String description) {
    }

    public record CandidateSkillCreateRequest(
            @NotBlank String skillId,
            @NotNull @Min(0) @Max(100) Integer proficiency,
            @DecimalMin("0.0") @DecimalMax("80.0") BigDecimal years,
            LocalDate lastUsedAt,
            @Size(max = 80) String source,
            Boolean primary) {
    }

    public record CandidateSkillUpdateRequest(
            @NotNull @Min(0) @Max(100) Integer proficiency,
            @DecimalMin("0.0") @DecimalMax("80.0") BigDecimal years,
            LocalDate lastUsedAt,
            @Size(max = 80) String source,
            Boolean primary) {
    }

    public record CandidateSkillView(
            String id, SkillView skill, Integer proficiency, BigDecimal years,
            LocalDate lastUsedAt, String source, Boolean primary, int version) {
    }
}


package com.jobpilot.dashboard.dto;

import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import java.time.LocalDateTime;

public record DashboardView(
        int profileCompleteness,
        long skillsCount,
        long projectsCount,
        long experiencesCount,
        long educationCount,
        long resumeCount,
        long resumeVersions,
        long totalJobs,
        long activeJobs,
        long parsedJobs,
        long parseFailedJobs,
        long ignoredJobs,
        long companies,
        long importTasks,
        LocalDateTime lastImportAt,
        ResumeSummaryView defaultResume,
        LocalDateTime lastUpdated,
        String matchingMessage) {
}

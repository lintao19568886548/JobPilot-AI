package com.jobpilot.onboarding.dto;

import java.time.Instant;
import java.util.List;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record ReadinessStepView(
            String key,
            String status,
            long currentValue,
            long targetValue,
            int weight,
            int earnedScore,
            String title,
            String description,
            String actionPath,
            boolean blocking) {
    }

    public record QualityIssueView(
            String code,
            String severity,
            String title,
            String description,
            String resourceType,
            String actionPath) {
    }

    public record QualitySummaryView(long blockers, long warnings, long info, long total) {
    }

    public record OnboardingOverviewView(
            int score,
            String status,
            boolean readyForMatching,
            long completedSteps,
            long totalSteps,
            List<ReadinessStepView> steps,
            QualitySummaryView qualitySummary,
            Instant generatedAt) {
    }

    public record DataQualityView(
            QualitySummaryView summary,
            List<QualityIssueView> issues,
            Instant generatedAt) {
    }
}

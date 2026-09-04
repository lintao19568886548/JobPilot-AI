package com.jobpilot.privacy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;

public final class PrivacyDtos {
    private PrivacyDtos() { }

    public record ExportView(String schemaVersion, OffsetDateTime generatedAt,
                             Map<String, Long> recordCounts, Map<String, Object> data) { }

    public record DeletionPreviewView(String requestId, int requestVersion, Map<String, Long> affectedRecords,
                                      boolean dataChanged, String confirmationPhrase, String warning,
                                      OffsetDateTime generatedAt) { }

    public record DeletionConfirmRequest(@NotBlank String requestId,
                                         @NotNull @Min(0) Integer requestVersion,
                                         @NotBlank String confirmationPhrase) { }

    public record DeletionResultView(String requestId, String status, Map<String, Long> affectedRecords,
                                     boolean userDisabled, OffsetDateTime completedAt) { }
}

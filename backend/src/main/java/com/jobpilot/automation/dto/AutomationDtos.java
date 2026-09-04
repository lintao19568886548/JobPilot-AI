package com.jobpilot.automation.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AutomationDtos {
    private AutomationDtos() { }

    public record WorkerField(String selector, String value) { }
    public record WorkerPrepareRequest(
            String schemaVersion,
            String taskId,
            String taskToken,
            String targetUrl,
            String queueApprovalId,
            String policyMode,
            boolean finalConfirmationRequired,
            List<WorkerField> fields) { }
    public record WorkerStep(String type, String status, String selector, String detail) { }
    public record WorkerPrepareResponse(
            String schemaVersion,
            String taskId,
            String status,
            boolean externallySubmitted,
            boolean applicationCreated,
            boolean finalConfirmationRequired,
            int submitCount,
            String blockedReason,
            List<WorkerStep> steps) { }

    public record AutomationStepView(
            String id, int sequenceNo, String type, String status,
            String selectorHint, String detail, LocalDateTime occurredAt, String traceId) { }
    public record AutomationTaskView(
            String id, String queueItemId, String deviceId, String taskType, String status,
            String targetUrl, boolean externallySubmitted, boolean applicationCreated,
            boolean finalConfirmationRequired, String errorCode, String errorMessage,
            LocalDateTime expiresAt, LocalDateTime startedAt, LocalDateTime completedAt,
            LocalDateTime createdAt, List<AutomationStepView> steps) { }
}

package com.jobpilot.automation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AutomationCenterDtos {
    private AutomationCenterDtos() { }
    public record RuleCreateRequest(@NotBlank @Size(max=160) String name,@NotBlank String ruleType,
                                    @NotBlank @Size(max=120) String scheduleExpression,@NotBlank @Size(max=64) String timezone,
                                    Map<String,Object> config,Boolean enabled,@Min(1) @Max(10) Integer maxAttempts){}
    public record RuleUpdateRequest(@NotNull Integer version,@Size(max=160) String name,
                                    @Size(max=120) String scheduleExpression,@Size(max=64) String timezone,
                                    Map<String,Object> config,Boolean enabled,@Min(1) @Max(10) Integer maxAttempts){}
    public record RuleRunRequest(Boolean defer){}
    public record RuleView(String id,int version,String name,String ruleType,String scheduleExpression,String timezone,
                           JsonNode config,boolean enabled,String status,int maxAttempts,LocalDateTime lastRunAt,
                           LocalDateTime nextRunAt,LocalDateTime createdAt){}
    public record TaskView(String id,int version,String ruleId,String taskType,String status,int attemptCount,int maxAttempts,
                           JsonNode input,JsonNode output,LocalDateTime scheduledAt,LocalDateTime nextRetryAt,
                           LocalDateTime startedAt,LocalDateTime finishedAt,String errorCode,String errorMessage,
                           boolean externalMessageSent,boolean externalSubmission,boolean externalMutation,
                           boolean userConfirmationRequired,LocalDateTime createdAt){}
    public record SuggestionView(String id,int version,String suggestionType,String resourceType,String resourceId,
                                 String title,String body,JsonNode evidence,String status,LocalDateTime decidedAt,LocalDateTime createdAt){}
    public record NotificationView(String id,int version,String notificationType,String title,String body,
                                   String resourceType,String resourceId,String status,LocalDateTime readAt,LocalDateTime createdAt){}
    public record AuthorizationRequest(@NotBlank @Size(max=80) String platform,@NotEmpty List<@NotBlank String> scopes,
                                       @NotBlank @Size(max=48) String evidenceType,@NotBlank @Size(max=500) String evidenceRef,
                                       @NotNull LocalDateTime expiresAt){}
    public record AuthorizationView(String id,int version,String platform,List<String> scopes,String evidenceType,
                                    String evidenceRef,LocalDateTime grantedAt,LocalDateTime expiresAt,
                                    LocalDateTime revokedAt,String status){}
    public record PolicyDecisionView(String platform,String requestedScope,boolean policyFresh,boolean authorizationActive,
                                     boolean allowed,String executionMode,String reason){}
    public record VersionRequest(@NotNull Integer version){}
    public record AutomationDashboard(long activeRules,long pausedRules,long pendingTasks,long failedTasks,
                                      long pendingSuggestions,long unreadNotifications,boolean schedulerEnabled,
                                      List<String> allowedHandlers,List<String> safetyBoundaries){}
}

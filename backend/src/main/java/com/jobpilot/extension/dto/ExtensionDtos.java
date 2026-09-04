package com.jobpilot.extension.dto;

import com.jobpilot.job.dto.JobDtos.JobDetailView;
import com.jobpilot.matching.dto.MatchingDtos.JobMatchView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.tailoring.dto.TailoringDtos.CommunicationDraftView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ExtensionDtos {
    private ExtensionDtos() { }

    public record PairingCodeView(String pairingCode, LocalDateTime expiresAt, List<String> scopes) { }
    public record PairingRequest(
            @NotBlank @Size(min=6,max=32) String pairingCode,
            @NotBlank @Size(max=120) String deviceName,
            @NotBlank @Size(max=80) String browserName,
            @NotBlank @Pattern(regexp="[a-p]{32}|fixture-extension") String extensionId,
            @NotBlank @Size(max=40) String extensionVersion) { }
    public record RefreshRequest(@NotBlank @Size(max=500) String refreshToken) { }
    public record ExtensionTokensView(String accessToken, String refreshToken, long expiresIn,
                                      ExtensionDeviceView device, List<String> scopes) { }
    public record ExtensionDeviceView(String id, String deviceName, String browserName,
                                      String extensionId, String extensionVersion, List<String> scopes,
                                      String status, LocalDateTime pairedAt, LocalDateTime lastSeenAt,
                                      LocalDateTime revokedAt) { }

    public record ExtensionWorkspaceView(JobDetailView job, JobMatchView latestMatch,
                                         ResumeSummaryView recommendedResume,
                                         CommunicationDraftView latestDraft,
                                         boolean externallySubmitted,
                                         boolean messageSent) { }
    public record ExtensionAnalyzeRequest(@Size(max=26) String resumeVersionId, Boolean force) { }
    public record ExtensionQueueRequest(@Size(max=26) String resumeVersionId,
                                        @Size(max=26) String jobMatchId,
                                        @Size(max=26) String draftId,
                                        Integer priority) { }
    public record ExtensionDraftRequest(
            @NotBlank @Pattern(regexp="BOSS|LIEPIN|EMAIL|WECHAT|THANK_YOU|FOLLOW_UP|OFFER") String channel,
            @Size(max=26) String resumeVersionId) { }
}

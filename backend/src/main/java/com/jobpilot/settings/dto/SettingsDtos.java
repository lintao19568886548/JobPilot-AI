package com.jobpilot.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record AccountView(
            String id,
            String username,
            String email,
            String displayName,
            String timezone,
            String locale,
            String status,
            LocalDateTime lastLoginAt,
            LocalDateTime passwordChangedAt,
            Integer version) {
    }

    public record SettingView(
            String group,
            String key,
            Object value,
            String valueType,
            String source,
            Integer version,
            LocalDateTime updatedAt) {
    }

    public record SecuritySummary(
            long activeWebSessions,
            boolean extensionPairingsManagedSeparately,
            LocalDateTime passwordChangedAt) {
    }

    public record SettingsOverview(
            AccountView account,
            List<SettingView> preferences,
            SecuritySummary security) {
    }

    public record AccountUpdateRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Size(max = 16) String locale,
            @NotNull Integer version) {
    }

    public record SettingUpdateRequest(@NotNull Object value, Integer version) {
    }
}

package com.jobpilot.auth.dto;

import com.jobpilot.auth.domain.UserEntity;
import java.time.LocalDateTime;

public record UserView(
        String id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String status,
        String timezone,
        String locale,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt) {

    public static UserView from(UserEntity user) {
        return new UserView(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getTimezone(),
                user.getLocale(),
                user.getLastLoginAt(),
                user.getPasswordChangedAt());
    }
}

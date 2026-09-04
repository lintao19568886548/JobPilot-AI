package com.jobpilot.auth.dto;

import java.time.LocalDateTime;

public record SessionView(
        String id,
        String clientType,
        String clientLabel,
        String ipMasked,
        boolean current,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt) {
}

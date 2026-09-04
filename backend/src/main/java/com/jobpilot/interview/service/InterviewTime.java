package com.jobpilot.interview.service;

import com.jobpilot.common.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class InterviewTime {
    private InterviewTime() { }

    public static LocalDateTime toUtc(OffsetDateTime value, String timezone) {
        validateZone(timezone);
        ZoneOffset expected = ZoneId.of(timezone).getRules().getOffset(value.toInstant());
        if (!expected.equals(value.getOffset())) {
            throw new ValidationException("Timestamp offset does not match timezone at that instant");
        }
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    public static OffsetDateTime fromUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static void validateZone(String timezone) {
        try {
            if (timezone == null || timezone.isBlank()) throw new IllegalArgumentException();
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new ValidationException("Invalid IANA timezone");
        }
    }

    public static void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ValidationException("scheduledEndAt must be after scheduledStartAt");
        }
    }

    public static void validateFuture(LocalDateTime value) {
        if (value == null || !value.toInstant(ZoneOffset.UTC).isAfter(Instant.now())) {
            throw new ValidationException("Pending reminder time must be in the future");
        }
    }
}

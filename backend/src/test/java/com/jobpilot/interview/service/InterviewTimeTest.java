package com.jobpilot.interview.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jobpilot.common.exception.ValidationException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class InterviewTimeTest {
    @Test void convertsShanghaiTimeToUtc() {
        LocalDateTime result = InterviewTime.toUtc(OffsetDateTime.parse("2026-09-03T10:00:00+08:00"), "Asia/Shanghai");
        assertEquals(LocalDateTime.parse("2026-09-03T02:00:00"), result);
    }

    @Test void acceptsNewYorkSummerOffset() {
        LocalDateTime result = InterviewTime.toUtc(OffsetDateTime.parse("2026-07-01T10:00:00-04:00"), "America/New_York");
        assertEquals(LocalDateTime.parse("2026-07-01T14:00:00"), result);
    }

    @Test void acceptsNewYorkWinterOffset() {
        LocalDateTime result = InterviewTime.toUtc(OffsetDateTime.parse("2026-12-01T10:00:00-05:00"), "America/New_York");
        assertEquals(LocalDateTime.parse("2026-12-01T15:00:00"), result);
    }

    @Test void rejectsOffsetThatDoesNotMatchZoneAtInstant() {
        assertThrows(ValidationException.class,
                () -> InterviewTime.toUtc(OffsetDateTime.parse("2026-07-01T10:00:00-05:00"), "America/New_York"));
    }

    @Test void rejectsUnknownTimezone() {
        assertThrows(ValidationException.class, () -> InterviewTime.validateZone("Mars/Olympus"));
    }

    @Test void rejectsInvalidRange() {
        assertThrows(ValidationException.class,
                () -> InterviewTime.validateRange(LocalDateTime.parse("2026-09-03T10:00:00"), LocalDateTime.parse("2026-09-03T09:00:00")));
    }

    @Test void rejectsPastPendingReminder() {
        assertThrows(ValidationException.class, () -> InterviewTime.validateFuture(LocalDateTime.parse("2020-01-01T00:00:00")));
    }
}

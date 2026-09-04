package com.jobpilot.offer.service;

import com.jobpilot.common.exception.ValidationException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class OfferTime {
    private OfferTime() { }

    public static LocalDateTime toUtc(OffsetDateTime value, String timezone) {
        if (value == null) return null;
        ZoneId zone = zone(timezone);
        ZoneOffset expected = zone.getRules().getOffset(value.toInstant());
        if (!expected.equals(value.getOffset())) throw new ValidationException("Offset does not match timezone at the supplied instant");
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    public static OffsetDateTime fromUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static void validateFuture(LocalDateTime value) {
        if (value == null || !value.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ValidationException("Deadline must be in the future");
        }
    }

    public static void validateZone(String timezone) { zone(timezone); }

    private static ZoneId zone(String timezone) {
        try { return ZoneId.of(timezone); }
        catch (DateTimeException exception) { throw new ValidationException("Unknown IANA timezone"); }
    }
}

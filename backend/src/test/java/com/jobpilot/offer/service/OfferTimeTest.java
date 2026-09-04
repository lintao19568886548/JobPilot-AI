package com.jobpilot.offer.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jobpilot.common.exception.ValidationException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class OfferTimeTest {
    @Test void convertsShanghaiDeadlineToUtc(){assertEquals(LocalDateTime.parse("2026-12-01T02:00:00"),OfferTime.toUtc(OffsetDateTime.parse("2026-12-01T10:00:00+08:00"),"Asia/Shanghai"));}
    @Test void rejectsMismatchedTimezoneOffset(){assertThrows(ValidationException.class,()->OfferTime.toUtc(OffsetDateTime.parse("2026-12-01T10:00:00+07:00"),"Asia/Shanghai"));}
    @Test void rejectsUnknownTimezone(){assertThrows(ValidationException.class,()->OfferTime.validateZone("Moon/Base"));}
}

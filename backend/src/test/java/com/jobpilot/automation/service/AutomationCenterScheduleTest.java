package com.jobpilot.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.common.exception.ValidationException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AutomationCenterScheduleTest {
    @Test
    void acceptsSpringCronAndIanaTimezone() {
        LocalDateTime next = AutomationCenterService.next("0 0 9 * * *", "Asia/Shanghai");
        assertThat(next).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void rejectsInvalidScheduleOrTimezone() {
        assertThatThrownBy(() -> AutomationCenterService.next("not-a-cron", "Mars/Base"))
                .isInstanceOf(ValidationException.class);
    }
}

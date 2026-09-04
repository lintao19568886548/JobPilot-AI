package com.jobpilot.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutomationTaskServiceTest {
    @Test
    void requestHashIsStableAndDoesNotExposeInput() {
        String value = "queue-id|device-id";
        String hash = AutomationTaskService.sha256(value);
        assertThat(hash).hasSize(64).isEqualTo(AutomationTaskService.sha256(value));
        assertThat(hash).doesNotContain(value);
    }
}

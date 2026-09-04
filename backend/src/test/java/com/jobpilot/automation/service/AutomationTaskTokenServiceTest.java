package com.jobpilot.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.automation.config.AutomationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AutomationTaskTokenServiceTest {
    @Test
    void createsSignedTaskBoundTokenWithoutExposingSecret() {
        AutomationProperties properties = new AutomationProperties();
        properties.setWorkerToken("test-worker-secret-at-least-24-characters");
        AutomationTaskTokenService service = new AutomationTaskTokenService(properties);

        String token = service.create("01M1TESTTASK00000000000000", Instant.ofEpochSecond(2_000_000_000L),
                "01M1QUEUE0000000000000000", "ASSIST_ALLOWED");

        assertThat(token).startsWith("jpt_").doesNotContain(properties.getWorkerToken());
        String encodedClaims = token.substring(4, token.indexOf('.'));
        assertThat(new String(Base64.getUrlDecoder().decode(encodedClaims), StandardCharsets.UTF_8))
                .isEqualTo("01M1TESTTASK00000000000000|2000000000|01M1QUEUE0000000000000000|ASSIST_ALLOWED");
        assertThat(token.substring(token.indexOf('.') + 1)).hasSize(43);
    }

    @Test
    void rejectsMissingWorkerSecret() {
        assertThatThrownBy(() -> new AutomationTaskTokenService(new AutomationProperties())
                .create("task", Instant.now().plusSeconds(30), "queue", "ASSIST_ALLOWED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 24");
    }
}

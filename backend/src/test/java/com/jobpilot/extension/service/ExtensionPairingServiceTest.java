package com.jobpilot.extension.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtensionPairingServiceTest {
    @Test
    void hashesCredentialsWithoutStoringPlaintext() {
        String hash = ExtensionPairingService.hash("JPTESTVALUE");
        assertThat(hash).hasSize(64).isEqualTo(ExtensionPairingService.hash("JPTESTVALUE"));
        assertThat(hash).doesNotContain("JPTESTVALUE");
    }

    @Test
    void scopeSetDoesNotGrantBrowserOrSubmissionAuthority() {
        assertThat(ExtensionPairingService.SCOPES)
                .containsExactly("JOB_CAPTURE", "MATCH_READ", "QUEUE_WRITE", "DRAFT_WRITE", "ASSIST_PREPARE")
                .doesNotContain("COOKIE_READ", "PASSWORD_READ", "EXTERNAL_SUBMIT", "MESSAGE_SEND");
    }
}

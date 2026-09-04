package com.jobpilot.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Phase14MigrationContractTest {

    @Test
    void migrationDefinesAccountVersionsSessionMetadataAndSettingsWhitelistStorage() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V12__account_security_and_settings.sql").readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("password_changed_at", "auth_version", "user_agent_hash", "ip_masked");
        assertThat(sql).contains("CREATE TABLE system_settings", "uk_system_settings_user_group_key");
        assertThat(sql).contains("chk_system_settings_sensitive_storage", "encrypted_value");
    }
}

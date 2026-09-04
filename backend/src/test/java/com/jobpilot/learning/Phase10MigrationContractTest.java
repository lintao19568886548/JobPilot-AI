package com.jobpilot.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase10MigrationContractTest {
    @Test
    void migrationKeepsFeedbackImmutableAndAutomationNonExternal() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V10__learning_automation.sql"));
        assertThat(sql).contains("CREATE TABLE feedback", "CREATE TABLE ltr_model_versions",
                "CREATE TABLE safe_automation_tasks", "CREATE TABLE automation_authorizations");
        assertThat(sql).contains("external_message_sent=0", "external_submission=0",
                "external_mutation=0", "user_confirmation_required=1");
        assertThat(sql).doesNotContain("ON DELETE CASCADE");
    }
}

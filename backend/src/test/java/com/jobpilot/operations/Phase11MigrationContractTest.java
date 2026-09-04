package com.jobpilot.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Phase11MigrationContractTest {
    @Test void migrationDefinesBudgetOperationalEvidenceAndSafetyConstraints() throws Exception {
        String sql=new String(getClass().getResourceAsStream("/db/migration/V11__operational_readiness.sql").readAllBytes(),StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE ai_budget_policies","CREATE TABLE operational_runs");
        assertThat(sql).contains("uk_operational_runs_idempotency","chk_operational_runs_type","artifact_sha256");
        assertThat(sql).contains("BACKUP","RESTORE_DRILL","DEPENDENCY_SCAN","SECRET_SCAN","LICENSE_SCAN","CONTAINER_SCAN","LOAD_TEST");
    }
}

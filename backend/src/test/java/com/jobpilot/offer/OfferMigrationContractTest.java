package com.jobpilot.offer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfferMigrationContractTest {
    @Test void phaseNineMigrationContainsRequiredImmutableAndOwnershipTables() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V9__offer_center_analytics.sql"));
        for(String table:new String[]{"offers","offer_benefits","offer_comparisons","offer_comparison_items","offer_deadlines","analytics_snapshots","privacy_operation_requests"})assertTrue(sql.contains("CREATE TABLE "+table));
        assertTrue(sql.contains("uk_offers_active_application"));assertTrue(sql.contains("uk_offer_comparisons_idempotency"));
        assertFalse(sql.toLowerCase().contains("drop table"));
    }
}

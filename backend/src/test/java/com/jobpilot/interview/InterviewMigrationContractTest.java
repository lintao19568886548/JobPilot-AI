package com.jobpilot.interview;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InterviewMigrationContractTest {
    @Test void migrationContainsAllPhase8FactTablesAndSafetyConstraints() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V8__interview_center.sql")) {
            assertNotNull(stream);
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String table : new String[]{"interviews", "interview_rounds", "interview_questions",
                "interview_answer_notes", "interview_reviews", "interview_review_items",
                "knowledge_gaps", "knowledge_gap_evidence", "interview_reminders"}) {
            assertTrue(sql.contains("CREATE TABLE " + table), table);
        }
        assertTrue(sql.contains("'PREDICTED','ACTUAL','MANUAL'"));
        assertTrue(sql.contains("'PROPOSED','ACTIVE','RESOLVED','DISMISSED'"));
        assertTrue(sql.contains("uk_interview_reviews_version"));
        assertTrue(sql.contains("uk_interview_reminders_active"));
        assertFalse(sql.toLowerCase().contains("on delete cascade"));
    }
}

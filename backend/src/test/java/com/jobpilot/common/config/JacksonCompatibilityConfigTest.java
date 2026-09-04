package com.jobpilot.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonCompatibilityConfigTest {

    @Test
    void providesLegacyMapperWithJavaTimeSupport() throws Exception {
        ObjectMapper mapper = new JacksonCompatibilityConfig().legacyObjectMapper();

        assertThat(mapper.writeValueAsString(LocalDate.of(2026, 9, 3)))
                .isEqualTo("\"2026-09-03\"");
    }
}

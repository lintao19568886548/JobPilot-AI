package com.jobpilot.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the existing Jackson 2 based domain codecs explicit while Spring Boot 4
 * uses Jackson 3 for its MVC defaults. Domain services and signed payloads must
 * continue to share one stable mapper until their wire format is migrated.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonCompatibilityConfig {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

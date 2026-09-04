package com.jobpilot.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.interview.client.InterviewAiClient;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewAgentContractTest {
    @Test
    void internalClientUsesHttp11ForUvicornCompatibility() throws Exception {
        InterviewAiClient client = new InterviewAiClient(new AiServiceProperties(), new ObjectMapper());
        Field field = InterviewAiClient.class.getDeclaredField("client");
        field.setAccessible(true);
        HttpClient value = (HttpClient) field.get(client);
        assertThat(value.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void semanticIdempotencyHashIgnoresTransportMetadata() throws Exception {
        InterviewAgentService service = serviceWithOnlyObjectMapper();
        Map<String, Object> first = payload("task-a", "trace-a", OffsetDateTime.now());
        Map<String, Object> second = payload("task-b", "trace-b", OffsetDateTime.now().plusSeconds(10));
        var method = InterviewAgentService.class.getDeclaredMethod("hashPayload", Map.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, first)).isEqualTo(method.invoke(service, second));
    }

    private static Map<String, Object> payload(String task, String trace, OffsetDateTime deadline) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", task); value.put("traceId", trace); value.put("deadlineAt", deadline);
        value.put("role", "Backend Engineer"); value.put("round", Map.of("roundType", "TECHNICAL"));
        return value;
    }

    private static InterviewAgentService serviceWithOnlyObjectMapper() {
        return new InterviewAgentService(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, new ObjectMapper().findAndRegisterModules());
    }
}

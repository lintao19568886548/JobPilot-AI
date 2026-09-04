package com.jobpilot.tailoring.client;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.tailoring.service.TailoringHashAccessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TailoringAiClient {
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public TailoringAiClient(AiServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties; this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds())).build();
    }

    public AiResult<AiTailorResponse> tailor(Map<String, Object> payload) {
        AiResult<AiTailorResponse> result = send("/internal/v1/resumes/tailor", payload, AiTailorResponse.class);
        AiTailorResponse value = result.response();
        if (!"resume-tailor-response-v1".equals(value.schemaVersion()) || !"READY".equals(value.status())
                || !"VERIFIED".equals(value.truthCheckStatus()) || value.changes() == null || value.changes().isEmpty()) {
            throw new BusinessException(5026001, "Resume Agent returned an invalid contract", HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    public AiResult<AiDraftResponse> draft(Map<String, Object> payload) {
        AiResult<AiDraftResponse> result = send("/internal/v1/communications/draft", payload, AiDraftResponse.class);
        AiDraftResponse value = result.response();
        if (!"communication-draft-response-v1".equals(value.schemaVersion()) || !"DRAFT".equals(value.status())
                || !"VERIFIED".equals(value.truthCheckStatus()) || !Boolean.FALSE.equals(value.externallySent())
                || value.content() == null || value.content().isBlank()) {
            throw new BusinessException(5026002, "Communication Agent returned an invalid contract", HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    private <T> AiResult<T> send(String path, Map<String, Object> payload, Class<T> type) {
        if (properties.getInternalToken() == null || properties.getInternalToken().isBlank()) {
            throw new BusinessException(5036001, "AI service internal credential is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + path))
                    .version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json").header("X-Internal-Token", properties.getInternalToken())
                    .header("X-Trace-Id", TraceContext.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new BusinessException(5026003, "Phase 6 AI service rejected the request", HttpStatus.BAD_GATEWAY);
            T value = objectMapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(response.body());
            return new AiResult<>(value, TailoringHashAccessor.sha256(body), TailoringHashAccessor.sha256(response.body()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(5026004, "Phase 6 AI request was interrupted", HttpStatus.BAD_GATEWAY);
        } catch (Exception exception) {
            throw new BusinessException(5026005, "Phase 6 AI request failed", HttpStatus.BAD_GATEWAY);
        }
    }
}

package com.jobpilot.interview.client;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.logging.TraceContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InterviewAiClient {
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public InterviewAiClient(AiServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties; this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public AiResult<AiPredictionResponse> predict(Map<String, Object> payload) {
        AiResult<AiPredictionResponse> result = send("/internal/v1/interviews/predict", payload, AiPredictionResponse.class);
        AiPredictionResponse response = result.response();
        if (!"interview-prediction-response-v1".equals(response.schemaVersion()) || !"READY".equals(response.status())
                || response.questions() == null || response.questions().isEmpty()
                || response.questions().stream().anyMatch(item -> !"PREDICTED".equals(item.sourceType()))) {
            throw new BusinessException(5028001, "Interview Agent returned an invalid prediction contract", HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    public AiResult<AiReviewResponse> review(Map<String, Object> payload) {
        AiResult<AiReviewResponse> result = send("/internal/v1/interviews/review", payload, AiReviewResponse.class);
        AiReviewResponse response = result.response();
        if (!"interview-review-response-v1".equals(response.schemaVersion()) || !"DRAFT".equals(response.status())
                || response.items() == null || response.items().isEmpty()) {
            throw new BusinessException(5028002, "Interview Agent returned an invalid review contract", HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    private <T> AiResult<T> send(String path, Map<String, Object> payload, Class<T> type) {
        if (properties.getInternalToken() == null || properties.getInternalToken().isBlank()) {
            throw new BusinessException(5038001, "AI service internal credential is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", properties.getInternalToken())
                    .header("X-Trace-Id", TraceContext.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException(5028003,
                        "Interview Agent rejected the request with HTTP " + response.statusCode()
                                + validationSummary(response.body()),
                        HttpStatus.BAD_GATEWAY);
            }
            T value = objectMapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(response.body());
            return new AiResult<>(value, sha256(body), sha256(response.body()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(5028004, "Interview Agent request was interrupted", HttpStatus.BAD_GATEWAY);
        } catch (Exception exception) {
            throw new BusinessException(5028005, "Interview Agent request failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private String validationSummary(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode detail = objectMapper.readTree(responseBody).path("detail");
            if (!detail.isArray() || detail.isEmpty()) return "";
            JsonNode first = detail.get(0);
            String location = first.path("loc").toString();
            String type = first.path("type").asText("validation_error");
            return " (" + type + " at " + location + ")";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

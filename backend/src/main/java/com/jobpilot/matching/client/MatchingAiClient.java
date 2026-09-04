package com.jobpilot.matching.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.config.MatchingProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.matching.dto.MatchingDtos.AiMatchResponse;
import com.jobpilot.matching.dto.MatchingDtos.EmbeddingRecord;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MatchingAiClient {
    private final AiServiceProperties ai;
    private final MatchingProperties matching;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public MatchingAiClient(AiServiceProperties ai, MatchingProperties matching, ObjectMapper objectMapper) {
        this.ai = ai; this.matching = matching; this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(ai.getConnectTimeoutSeconds())).build();
    }

    public AiCallResult evaluate(Map<String, Object> payload) {
        if (ai.getInternalToken() == null || ai.getInternalToken().isBlank()) {
            throw new BusinessException(5032001, "AI service internal credential is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(ai.getBaseUrl() + "/internal/v1/matches/evaluate"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(matching.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", ai.getInternalToken())
                    .header("X-Trace-Id", TraceContext.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new BusinessException(5022001, "Matching AI service is unavailable", HttpStatus.BAD_GATEWAY);
            AiMatchResponse result = objectMapper.readerFor(AiMatchResponse.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(response.body());
            if (!"match-evaluate-response-v1".equals(result.schemaVersion())) throw new BusinessException(5022002, "Matching AI schema version is unsupported", HttpStatus.BAD_GATEWAY);
            if (result.embeddingScore() == null || result.embeddingScore().signum() < 0 || result.embeddingScore().compareTo(new java.math.BigDecimal("100")) > 0) {
                throw new BusinessException(5022003, "Matching AI returned an invalid embedding score", HttpStatus.BAD_GATEWAY);
            }
            List<EmbeddingRecord> records = new ArrayList<>();
            if (result.embedding() == null || !"embedding-response-v1".equals(result.embedding().path("schemaVersion").asText())
                    || result.embedding().path("dimension").asInt(0) <= 0 || result.embedding().path("model").asText().isBlank()) {
                throw new BusinessException(5022004, "Matching AI returned invalid embedding metadata", HttpStatus.BAD_GATEWAY);
            }
            if (!List.of("SUCCEEDED","FAILED","SKIPPED_NOT_CONFIGURED").contains(result.llmStatus())
                    || ("SUCCEEDED".equals(result.llmStatus()) && result.llmScore() == null)
                    || ("SKIPPED_NOT_CONFIGURED".equals(result.llmStatus()) && result.llmScore() != null)) {
                throw new BusinessException(5022007, "Matching AI returned an inconsistent LLM result", HttpStatus.BAD_GATEWAY);
            }
            JsonNode recordNodes = result.embedding().path("records");
            if (!recordNodes.isArray() || recordNodes.isEmpty()) throw new BusinessException(5022004, "Matching AI returned no embedding records", HttpStatus.BAD_GATEWAY);
            recordNodes.forEach(node -> {
                EmbeddingRecord record=objectMapper.convertValue(node,EmbeddingRecord.class);
                if(record.dimension()==null||record.dimension()!=result.embedding().path("dimension").asInt()||record.vectorId()==null||record.contentHash()==null)
                    throw new BusinessException(5022008,"Matching AI returned an invalid embedding record",HttpStatus.BAD_GATEWAY);
                records.add(record);
            });
            return new AiCallResult(result, records, sha256(body), sha256(response.body()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(5022005, "Matching AI request was interrupted", HttpStatus.BAD_GATEWAY);
        } catch (Exception exception) {
            throw new BusinessException(5022006, "Matching AI request failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    public record AiCallResult(AiMatchResponse response, List<EmbeddingRecord> embeddings, String requestHash, String responseHash) { }
}

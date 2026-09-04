package com.jobpilot.job.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.job.domain.JobEntity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JobParserClient {
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public JobParserClient(AiServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public JsonNode parse(JobEntity job, String companyName) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("title", job.getTitle());
            input.put("companyName", companyName);
            input.put("city", job.getCity());
            input.put("salaryText", job.getSalaryText());
            input.put("description", job.getDescriptionRaw());
            input.put("contentHash", job.getRawContentHash());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/internal/v1/jobs/parse"))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", properties.getInternalToken())
                    .header("X-Trace-Id", TraceContext.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(input)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(5032101, "AI parser is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (!result.isObject() || !result.hasNonNull("schemaVersion") || !result.hasNonNull("parserVersion")) {
                throw new BusinessException(5022102, "AI parser returned an invalid schema", HttpStatus.BAD_GATEWAY);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(5032101, "AI parser request was interrupted", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception exception) {
            throw new BusinessException(5032101, "AI parser is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}

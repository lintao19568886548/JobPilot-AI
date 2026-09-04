package com.jobpilot.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.exception.ValidationException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ValidationException("Invalid JSON content");
        }
    }

    public String writeNode(JsonNode value) {
        return value == null ? "{}" : value.toString();
    }

    public JsonNode toNode(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return value == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(value);
    }

    public List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON is invalid", exception);
        }
    }

    public JsonNode readNode(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON is invalid", exception);
        }
    }
}

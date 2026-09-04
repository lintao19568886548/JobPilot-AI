package com.jobpilot.common.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.cors")
public class CorsProperties {

    private String allowedOrigins = "http://localhost:5173";
    private String extensionAllowedOriginPatterns = "chrome-extension://*";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getExtensionAllowedOriginPatterns() {
        return extensionAllowedOriginPatterns;
    }

    public void setExtensionAllowedOriginPatterns(String extensionAllowedOriginPatterns) {
        this.extensionAllowedOriginPatterns = extensionAllowedOriginPatterns;
    }

    public List<String> origins() {
        return values(allowedOrigins);
    }

    public List<String> extensionOriginPatterns() {
        return values(extensionAllowedOriginPatterns);
    }

    private List<String> values(String source) {
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}

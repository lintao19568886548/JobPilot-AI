package com.jobpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jobpilot.matching")
public class MatchingProperties {
    private int readTimeoutSeconds = 600;
    private int pollIntervalMs = 1000;
    private int maxAttempts = 3;
    private int initialBackoffSeconds = 2;
    private String workerId = "jobpilot-local-worker";

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int value) { this.readTimeoutSeconds = value; }
    public int getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(int value) { this.pollIntervalMs = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public int getInitialBackoffSeconds() { return initialBackoffSeconds; }
    public void setInitialBackoffSeconds(int value) { this.initialBackoffSeconds = value; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String value) { this.workerId = value; }
}

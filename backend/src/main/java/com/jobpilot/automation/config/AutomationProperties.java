package com.jobpilot.automation.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jobpilot.automation")
public class AutomationProperties {
    private boolean enabled;
    private String workerBaseUrl = "http://127.0.0.1:8020";
    private String workerToken = "";
    private long taskExpirationSeconds = 300;
    private Set<String> allowedTargetHosts = Set.of("127.0.0.1", "localhost");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWorkerBaseUrl() { return workerBaseUrl; }
    public void setWorkerBaseUrl(String workerBaseUrl) { this.workerBaseUrl = workerBaseUrl; }
    public String getWorkerToken() { return workerToken; }
    public void setWorkerToken(String workerToken) { this.workerToken = workerToken; }
    public long getTaskExpirationSeconds() { return taskExpirationSeconds; }
    public void setTaskExpirationSeconds(long taskExpirationSeconds) { this.taskExpirationSeconds = taskExpirationSeconds; }
    public Set<String> getAllowedTargetHosts() { return allowedTargetHosts; }
    public void setAllowedTargetHosts(Set<String> allowedTargetHosts) { this.allowedTargetHosts = allowedTargetHosts; }
}

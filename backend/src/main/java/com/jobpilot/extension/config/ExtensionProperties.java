package com.jobpilot.extension.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jobpilot.extension")
public class ExtensionProperties {
    private long pairingExpirationSeconds = 300;
    private long accessExpirationSeconds = 900;
    private long refreshExpirationSeconds = 604800;

    public long getPairingExpirationSeconds() { return pairingExpirationSeconds; }
    public void setPairingExpirationSeconds(long value) { this.pairingExpirationSeconds = value; }
    public long getAccessExpirationSeconds() { return accessExpirationSeconds; }
    public void setAccessExpirationSeconds(long value) { this.accessExpirationSeconds = value; }
    public long getRefreshExpirationSeconds() { return refreshExpirationSeconds; }
    public void setRefreshExpirationSeconds(long value) { this.refreshExpirationSeconds = value; }
}

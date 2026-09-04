package com.jobpilot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.security")
public class JwtProperties {

    private String jwtSecret;
    private long accessExpirationSeconds;
    private long refreshExpirationSeconds;
    private String issuer;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public void setAccessExpirationSeconds(long accessExpirationSeconds) {
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    public void setRefreshExpirationSeconds(long refreshExpirationSeconds) {
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}


package com.jobpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jobpilot.recommendation")
public class RecommendationProperties {
    private int batchMaxSize = 100;
    private int batchPollIntervalMs = 1000;
    private String analyticsTimezone = "Asia/Shanghai";
    private int dashboardCacheTtlSeconds = 30;
    private int globalSearchMaxResults = 20;

    public int getBatchMaxSize() { return batchMaxSize; }
    public void setBatchMaxSize(int value) { this.batchMaxSize = value; }
    public int getBatchPollIntervalMs() { return batchPollIntervalMs; }
    public void setBatchPollIntervalMs(int value) { this.batchPollIntervalMs = value; }
    public String getAnalyticsTimezone() { return analyticsTimezone; }
    public void setAnalyticsTimezone(String value) { this.analyticsTimezone = value; }
    public int getDashboardCacheTtlSeconds() { return dashboardCacheTtlSeconds; }
    public void setDashboardCacheTtlSeconds(int value) { this.dashboardCacheTtlSeconds = value; }
    public int getGlobalSearchMaxResults() { return globalSearchMaxResults; }
    public void setGlobalSearchMaxResults(int value) { this.globalSearchMaxResults = value; }
}

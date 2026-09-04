package com.jobpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jobpilot.job-import")
public class JobImportProperties {
    private long maxFileBytes = 5_242_880;
    private int maxRows = 1_000;
    private int maxCellLength = 20_000;
    private int urlMaxBytes = 2_097_152;
    private int urlMaxRedirects = 3;

    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long value) { this.maxFileBytes = value; }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int value) { this.maxRows = value; }
    public int getMaxCellLength() { return maxCellLength; }
    public void setMaxCellLength(int value) { this.maxCellLength = value; }
    public int getUrlMaxBytes() { return urlMaxBytes; }
    public void setUrlMaxBytes(int value) { this.urlMaxBytes = value; }
    public int getUrlMaxRedirects() { return urlMaxRedirects; }
    public void setUrlMaxRedirects(int value) { this.urlMaxRedirects = value; }
}

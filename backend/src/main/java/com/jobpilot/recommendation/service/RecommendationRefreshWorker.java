package com.jobpilot.recommendation.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecommendationRefreshWorker {
    private final RecommendationRefreshService refreshService;

    public RecommendationRefreshWorker(RecommendationRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(fixedDelayString = "${jobpilot.recommendation.batch-poll-interval-ms:1000}")
    public void poll() {
        refreshService.synchronizeRunning();
    }
}

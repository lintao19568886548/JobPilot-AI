package com.jobpilot.recommendation.controller;

import static com.jobpilot.recommendation.dto.RecommendationDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.recommendation.service.RecommendationRefreshService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationRefreshController {
    private final RecommendationRefreshService service;

    public RecommendationRefreshController(RecommendationRefreshService service) {
        this.service = service;
    }

    @PostMapping({"/api/recommendation-refresh-runs", "/api/v1/recommendation-refresh-runs"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RecommendationRefreshRunView> start(
            @Valid @RequestBody RecommendationRefreshRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.success(service.start(CurrentUser.require().userId(), request, idempotencyKey));
    }

    @GetMapping({"/api/recommendation-refresh-runs/{id}", "/api/v1/recommendation-refresh-runs/{id}"})
    public ApiResponse<RecommendationRefreshRunView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PostMapping({"/api/recommendation-refresh-runs/{id}:retry-failed", "/api/v1/recommendation-refresh-runs/{id}:retry-failed"})
    public ApiResponse<RecommendationRefreshRunView> retryFailed(@PathVariable String id) {
        return ApiResponse.success(service.retryFailed(CurrentUser.require().userId(), id));
    }
}

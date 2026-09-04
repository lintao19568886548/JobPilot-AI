package com.jobpilot.analytics.controller;

import static com.jobpilot.analytics.dto.AnalyticsDtos.*;

import com.jobpilot.analytics.service.AnalyticsService;
import com.jobpilot.analytics.service.CompleteAnalyticsService;
import static com.jobpilot.analytics.dto.CompleteAnalyticsDtos.*;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {
    private final AnalyticsService service;
    private final CompleteAnalyticsService complete;

    public AnalyticsController(AnalyticsService service, CompleteAnalyticsService complete) {
        this.service = service; this.complete = complete;
    }

    @GetMapping({"/api/analytics/dashboard", "/api/v1/analytics/dashboard"})
    public ApiResponse<AnalyticsDashboardView> dashboard() {
        return ApiResponse.success(service.dashboard(CurrentUser.require().userId()));
    }

    @GetMapping({"/api/analytics/levels", "/api/v1/analytics/levels"})
    public ApiResponse<List<MetricPoint>> levels() {
        return ApiResponse.success(service.levels(CurrentUser.require().userId()));
    }

    @GetMapping({"/api/analytics/sources", "/api/v1/analytics/sources"})
    public ApiResponse<List<MetricPoint>> sources() {
        return ApiResponse.success(service.sources(CurrentUser.require().userId()));
    }

    @GetMapping({"/api/analytics/funnel", "/api/v1/analytics/funnel"})
    public ApiResponse<List<FunnelStage>> funnel() {
        return ApiResponse.success(service.funnel(CurrentUser.require().userId()));
    }

    @PostMapping("/api/analytics:rebuild")
    public ApiResponse<RebuildResult> rebuild(@Valid @RequestBody RebuildRequest request) {
        return ApiResponse.success(service.rebuild(CurrentUser.require().userId(), request.from(), request.to()));
    }

    @GetMapping("/api/v1/analytics/complete")
    public ApiResponse<CompleteAnalyticsView> complete(@org.springframework.web.bind.annotation.RequestParam(required=false) LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required=false) LocalDate to) {
        LocalDate end=Optional.ofNullable(to).orElse(LocalDate.now()); LocalDate start=Optional.ofNullable(from).orElse(end.minusDays(89));
        return ApiResponse.success(complete.calculate(CurrentUser.require().userId(),start,end));
    }

    @PostMapping({"/api/v1/analytics:rebuild","/api/v1/analytics/complete:rebuild"})
    public ApiResponse<SnapshotView> completeRebuild(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody SnapshotRequest request) { return ApiResponse.success(complete.rebuild(CurrentUser.require().userId(),key,request)); }

    @GetMapping("/api/v1/analytics/snapshots")
    public ApiResponse<List<SnapshotView>> snapshots() { return ApiResponse.success(complete.snapshots(CurrentUser.require().userId())); }

    @GetMapping("/api/v1/analytics/snapshots/{id}")
    public ApiResponse<SnapshotView> snapshot(@org.springframework.web.bind.annotation.PathVariable String id) { return ApiResponse.success(complete.snapshot(CurrentUser.require().userId(),id)); }
}

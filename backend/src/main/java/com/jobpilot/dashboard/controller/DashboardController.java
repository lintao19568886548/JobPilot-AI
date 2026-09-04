package com.jobpilot.dashboard.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.dashboard.dto.DashboardView;
import com.jobpilot.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping({"/api/dashboard", "/api/v1/dashboard"})
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.success(service.get(CurrentUser.require().userId()));
    }
}


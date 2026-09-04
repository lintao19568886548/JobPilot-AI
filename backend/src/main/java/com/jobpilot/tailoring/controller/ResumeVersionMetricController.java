package com.jobpilot.tailoring.controller;

import static com.jobpilot.tailoring.dto.TailoringDtos.ResumeVersionMetricView;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.tailoring.service.ResumeVersionMetricService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeVersionMetricController {
    private final ResumeVersionMetricService service;
    public ResumeVersionMetricController(ResumeVersionMetricService service) { this.service = service; }

    @GetMapping("/api/v1/resume-versions/{id}/metrics")
    public ApiResponse<ResumeVersionMetricView> get(@PathVariable String id) {
        return ApiResponse.success(service.calculate(CurrentUser.require().userId(), id));
    }
}

package com.jobpilot.onboarding.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.onboarding.dto.OnboardingDtos.DataQualityView;
import com.jobpilot.onboarding.dto.OnboardingDtos.OnboardingOverviewView;
import com.jobpilot.onboarding.service.OnboardingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<OnboardingOverviewView> overview() {
        return ApiResponse.success(service.overview(CurrentUser.require().userId()));
    }

    @GetMapping("/data-quality")
    public ApiResponse<DataQualityView> dataQuality() {
        return ApiResponse.success(service.dataQuality(CurrentUser.require().userId()));
    }
}

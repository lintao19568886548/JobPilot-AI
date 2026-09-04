package com.jobpilot.application.controller;

import static com.jobpilot.application.dto.ApplicationDtos.PolicyDecisionView;
import static com.jobpilot.application.dto.ApplicationDtos.PolicyUpdateRequest;

import com.jobpilot.application.service.PlatformPolicyService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
public class PlatformPolicyController {
    private final PlatformPolicyService service;

    public PlatformPolicyController(PlatformPolicyService service) { this.service = service; }

    @GetMapping("/api/v1/platform-policies/{platform}")
    public ApiResponse<PolicyDecisionView> get(@PathVariable String platform) {
        CurrentUser.require();
        return ApiResponse.success(service.resolve(platform));
    }

    @PutMapping("/api/v1/platform-policies/{platform}")
    public ApiResponse<PolicyDecisionView> configure(@PathVariable String platform,
                                                     @Valid @RequestBody PolicyUpdateRequest request) {
        return ApiResponse.success(service.configure(CurrentUser.require().userId(), platform, request));
    }
}

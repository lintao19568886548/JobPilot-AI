package com.jobpilot.candidate.controller;

import com.jobpilot.candidate.dto.CandidateDtos.CompletenessView;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileView;
import com.jobpilot.candidate.service.CandidateProfileService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidateProfileController {

    private final CandidateProfileService service;

    public CandidateProfileController(CandidateProfileService service) {
        this.service = service;
    }

    @GetMapping({"/api/candidate/profile", "/api/v1/candidate-profile"})
    public ApiResponse<ProfileView> get() {
        return ApiResponse.success(service.get(CurrentUser.require().userId()));
    }

    @PutMapping({"/api/candidate/profile", "/api/v1/candidate-profile"})
    public ApiResponse<ProfileView> replace(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.success(service.replace(CurrentUser.require().userId(), request));
    }

    @PatchMapping({"/api/candidate/profile", "/api/v1/candidate-profile"})
    public ApiResponse<ProfileView> patch(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.success(service.patch(CurrentUser.require().userId(), request));
    }

    @GetMapping({"/api/candidate/profile/completeness", "/api/v1/candidate-profile/completeness"})
    public ApiResponse<CompletenessView> completeness() {
        return ApiResponse.success(service.completeness(CurrentUser.require().userId()));
    }
}


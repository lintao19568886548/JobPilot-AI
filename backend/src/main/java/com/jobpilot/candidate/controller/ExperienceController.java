package com.jobpilot.candidate.controller;

import com.jobpilot.candidate.dto.CandidateDtos.ExperienceRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ExperienceView;
import com.jobpilot.candidate.service.ExperienceService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExperienceController {

    private final ExperienceService service;

    public ExperienceController(ExperienceService service) {
        this.service = service;
    }

    @GetMapping({"/api/candidate/experiences", "/api/v1/candidate-profile/experiences"})
    public ApiResponse<List<ExperienceView>> list() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @PostMapping({"/api/candidate/experiences", "/api/v1/candidate-profile/experiences"})
    public ApiResponse<ExperienceView> create(@Valid @RequestBody ExperienceRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @PutMapping({"/api/candidate/experiences/{id}", "/api/v1/candidate-profile/experiences/{id}"})
    public ApiResponse<ExperienceView> update(@PathVariable String id, @Valid @RequestBody ExperienceRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping({"/api/candidate/experiences/{id}", "/api/v1/candidate-profile/experiences/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }
}


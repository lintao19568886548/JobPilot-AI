package com.jobpilot.candidate.controller;

import com.jobpilot.candidate.dto.CandidateDtos.EducationRequest;
import com.jobpilot.candidate.dto.CandidateDtos.EducationView;
import com.jobpilot.candidate.service.EducationService;
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
public class EducationController {

    private final EducationService service;

    public EducationController(EducationService service) {
        this.service = service;
    }

    @GetMapping({"/api/candidate/educations", "/api/v1/candidate-profile/educations"})
    public ApiResponse<List<EducationView>> list() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @PostMapping({"/api/candidate/educations", "/api/v1/candidate-profile/educations"})
    public ApiResponse<EducationView> create(@Valid @RequestBody EducationRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @PutMapping({"/api/candidate/educations/{id}", "/api/v1/candidate-profile/educations/{id}"})
    public ApiResponse<EducationView> update(@PathVariable String id, @Valid @RequestBody EducationRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping({"/api/candidate/educations/{id}", "/api/v1/candidate-profile/educations/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }
}


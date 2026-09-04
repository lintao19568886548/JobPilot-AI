package com.jobpilot.candidate.controller;

import com.jobpilot.candidate.dto.CandidateDtos.ProjectRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ProjectView;
import com.jobpilot.candidate.service.ProjectService;
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
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping({"/api/candidate/projects", "/api/v1/candidate-profile/projects"})
    public ApiResponse<List<ProjectView>> list() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @PostMapping({"/api/candidate/projects", "/api/v1/candidate-profile/projects"})
    public ApiResponse<ProjectView> create(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @PutMapping({"/api/candidate/projects/{id}", "/api/v1/candidate-profile/projects/{id}"})
    public ApiResponse<ProjectView> update(@PathVariable String id, @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping({"/api/candidate/projects/{id}", "/api/v1/candidate-profile/projects/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }
}


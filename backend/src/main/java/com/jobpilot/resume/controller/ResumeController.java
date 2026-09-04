package com.jobpilot.resume.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.resume.dto.ResumeDtos.ResumeCreateRequest;
import com.jobpilot.resume.dto.ResumeDtos.ResumeDetailView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeUpdateRequest;
import com.jobpilot.resume.dto.ResumeDtos.ResumeVersionSummaryView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeVersionView;
import com.jobpilot.resume.dto.ResumeDtos.VersionCreateRequest;
import com.jobpilot.resume.service.ResumeService;
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
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @GetMapping({"/api/resumes", "/api/v1/resumes"})
    public ApiResponse<List<ResumeSummaryView>> list() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @PostMapping({"/api/resumes", "/api/v1/resumes"})
    public ApiResponse<ResumeDetailView> create(@Valid @RequestBody ResumeCreateRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @GetMapping({"/api/resumes/{id}", "/api/v1/resumes/{id}"})
    public ApiResponse<ResumeDetailView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PutMapping({"/api/resumes/{id}", "/api/v1/resumes/{id}"})
    public ApiResponse<ResumeDetailView> update(
            @PathVariable String id,
            @Valid @RequestBody ResumeUpdateRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping({"/api/resumes/{id}", "/api/v1/resumes/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping({"/api/resumes/{id}/versions", "/api/v1/resumes/{id}/versions"})
    public ApiResponse<ResumeVersionView> createVersion(
            @PathVariable String id,
            @Valid @RequestBody VersionCreateRequest request) {
        return ApiResponse.success(service.createVersion(CurrentUser.require().userId(), id, request));
    }

    @GetMapping({"/api/resumes/{id}/versions", "/api/v1/resumes/{id}/versions"})
    public ApiResponse<List<ResumeVersionSummaryView>> versions(@PathVariable String id) {
        return ApiResponse.success(service.versions(CurrentUser.require().userId(), id));
    }

    @GetMapping({"/api/resume-versions/{id}", "/api/v1/resume-versions/{id}"})
    public ApiResponse<ResumeVersionView> version(@PathVariable String id) {
        return ApiResponse.success(service.version(CurrentUser.require().userId(), id));
    }

    @PostMapping({"/api/resumes/{id}/set-default", "/api/v1/resumes/{id}/set-default"})
    public ApiResponse<ResumeDetailView> setDefault(@PathVariable String id) {
        return ApiResponse.success(service.setDefault(CurrentUser.require().userId(), id));
    }

    @PostMapping({"/api/resumes/{id}/set-master", "/api/v1/resumes/{id}/set-master"})
    public ApiResponse<ResumeDetailView> setMaster(@PathVariable String id) {
        return ApiResponse.success(service.setMaster(CurrentUser.require().userId(), id));
    }
}


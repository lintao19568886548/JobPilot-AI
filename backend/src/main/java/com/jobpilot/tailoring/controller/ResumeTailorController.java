package com.jobpilot.tailoring.controller;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.tailoring.service.ResumeTailorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeTailorController {
    private final ResumeTailorService service;
    public ResumeTailorController(ResumeTailorService service) { this.service = service; }

    @GetMapping("/api/v1/resume-tailor-runs")
    public ApiResponse<List<TailorRunView>> list() { return ApiResponse.success(service.list(CurrentUser.require().userId())); }

    @PostMapping("/api/v1/jobs/{jobId}/resume-tailor-runs")
    public ApiResponse<TailorRunView> create(@PathVariable String jobId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TailorCreateRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), jobId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/resume-tailor-runs/{id}")
    public ApiResponse<TailorRunView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PostMapping("/api/v1/resume-tailor-runs/{id}:approve")
    public ApiResponse<TailorRunView> approve(@PathVariable String id, @Valid @RequestBody TailorApproveRequest request) {
        return ApiResponse.success(service.approve(CurrentUser.require().userId(), id, request));
    }
}

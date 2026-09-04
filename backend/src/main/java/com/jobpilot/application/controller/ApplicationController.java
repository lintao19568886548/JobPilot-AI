package com.jobpilot.application.controller;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.jobpilot.application.service.ApplicationService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) { this.service = service; }

    @GetMapping("/api/v1/applications")
    public ApiResponse<ApplicationPage> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(CurrentUser.require().userId(), status));
    }

    @PostMapping("/api/v1/applications")
    public ApiResponse<ApplicationView> create(@RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody ApplicationCreateRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), key, request));
    }

    @GetMapping("/api/v1/applications/{id}")
    public ApiResponse<ApplicationView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PatchMapping("/api/v1/applications/{id}")
    public ApiResponse<ApplicationView> update(@PathVariable String id,
                                               @Valid @RequestBody ApplicationUpdateRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @PostMapping("/api/v1/applications/{id}/transitions")
    public ApiResponse<ApplicationView> transition(@PathVariable String id,
                                                   @Valid @RequestBody TransitionRequest request) {
        return ApiResponse.success(service.transition(CurrentUser.require().userId(), id, request));
    }

    @GetMapping("/api/v1/applications/{id}/logs")
    public ApiResponse<List<ApplicationLogView>> logs(@PathVariable String id) {
        return ApiResponse.success(service.logs(CurrentUser.require().userId(), id));
    }

    @GetMapping("/api/v1/applications/kpis")
    public ApiResponse<ApplicationKpis> kpis() {
        return ApiResponse.success(service.kpis(CurrentUser.require().userId()));
    }
}

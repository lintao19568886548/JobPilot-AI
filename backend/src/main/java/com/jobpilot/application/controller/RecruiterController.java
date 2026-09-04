package com.jobpilot.application.controller;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.jobpilot.application.service.RecruiterService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecruiterController {
    private final RecruiterService service;

    public RecruiterController(RecruiterService service) { this.service = service; }

    @GetMapping("/api/v1/recruiters")
    public ApiResponse<List<RecruiterView>> list() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @PostMapping("/api/v1/recruiters")
    public ApiResponse<RecruiterView> create(@Valid @RequestBody RecruiterRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @GetMapping("/api/v1/recruiters/{id}")
    public ApiResponse<RecruiterView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PatchMapping("/api/v1/recruiters/{id}")
    public ApiResponse<RecruiterView> update(@PathVariable String id,
                                             @Valid @RequestBody RecruiterPatchRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @GetMapping("/api/v1/recruiters/{id}/interactions")
    public ApiResponse<List<InteractionView>> interactions(@PathVariable String id) {
        return ApiResponse.success(service.interactions(CurrentUser.require().userId(), id));
    }

    @PostMapping("/api/v1/recruiters/{id}/interactions")
    public ApiResponse<InteractionView> addInteraction(@PathVariable String id,
                                                       @Valid @RequestBody InteractionRequest request) {
        return ApiResponse.success(service.addInteraction(CurrentUser.require().userId(), id, request));
    }
}

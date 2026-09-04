package com.jobpilot.tailoring.controller;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.tailoring.service.CommunicationDraftService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunicationDraftController {
    private final CommunicationDraftService service;
    public CommunicationDraftController(CommunicationDraftService service) { this.service = service; }

    @GetMapping("/api/v1/communication-drafts")
    public ApiResponse<List<CommunicationDraftView>> list() { return ApiResponse.success(service.list(CurrentUser.require().userId())); }

    @PostMapping("/api/v1/jobs/{jobId}/communication-drafts")
    public ApiResponse<CommunicationDraftView> create(@PathVariable String jobId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DraftCreateRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), jobId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/communication-drafts/{id}")
    public ApiResponse<CommunicationDraftView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @PostMapping("/api/v1/communication-drafts/{id}:approve")
    public ApiResponse<CommunicationDraftView> approve(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success(service.approve(CurrentUser.require().userId(), id, request));
    }

    @PostMapping("/api/v1/communication-drafts/{id}:mark-used")
    public ApiResponse<CommunicationDraftView> markUsed(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success(service.markUsed(CurrentUser.require().userId(), id, request));
    }
}

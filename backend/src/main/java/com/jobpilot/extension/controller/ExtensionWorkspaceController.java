package com.jobpilot.extension.controller;

import static com.jobpilot.application.dto.ApplicationDtos.QueueItemView;
import static com.jobpilot.extension.dto.ExtensionDtos.*;
import static com.jobpilot.matching.dto.MatchingDtos.MatchRunView;
import static com.jobpilot.tailoring.dto.TailoringDtos.CommunicationDraftView;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.extension.service.ExtensionWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExtensionWorkspaceController {
    private final ExtensionWorkspaceService service;
    public ExtensionWorkspaceController(ExtensionWorkspaceService service) { this.service = service; }

    @GetMapping("/api/v1/extension/jobs/{jobId}/workspace")
    public ApiResponse<ExtensionWorkspaceView> workspace(@PathVariable String jobId) {
        return ApiResponse.success(service.workspace(CurrentUser.require().userId(), jobId));
    }

    @PostMapping("/api/v1/extension/jobs/{jobId}:analyze")
    public ResponseEntity<ApiResponse<MatchRunView>> analyze(@PathVariable String jobId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ExtensionAnalyzeRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(service.analyze(CurrentUser.require().userId(), jobId, key, request)));
    }

    @PostMapping("/api/v1/extension/jobs/{jobId}/queue")
    public ApiResponse<QueueItemView> enqueue(@PathVariable String jobId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ExtensionQueueRequest request) {
        return ApiResponse.success(service.enqueue(CurrentUser.require().userId(), jobId, key, request));
    }

    @PostMapping("/api/v1/extension/jobs/{jobId}/communication-drafts")
    public ApiResponse<CommunicationDraftView> draft(@PathVariable String jobId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ExtensionDraftRequest request) {
        return ApiResponse.success(service.draft(CurrentUser.require().userId(), jobId, key, request));
    }
}

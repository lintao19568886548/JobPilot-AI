package com.jobpilot.application.controller;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.jobpilot.application.service.ApplicationQueueService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationQueueController {
    private final ApplicationQueueService service;

    public ApplicationQueueController(ApplicationQueueService service) { this.service = service; }

    @GetMapping("/api/v1/application-queue")
    public ApiResponse<List<QueueItemView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(CurrentUser.require().userId(), status));
    }

    @PostMapping("/api/v1/application-queue/items")
    public ApiResponse<QueueItemView> enqueue(@RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody QueueEnqueueRequest request) {
        return ApiResponse.success(service.enqueue(CurrentUser.require().userId(), key, request));
    }

    @PostMapping("/api/v1/application-queue/items/batch")
    public ApiResponse<QueueBatchResult> batch(@RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody QueueBatchRequest request) {
        return ApiResponse.success(service.enqueueBatch(CurrentUser.require().userId(), key, request));
    }

    @PatchMapping("/api/v1/application-queue/items/{id}")
    public ApiResponse<QueueItemView> update(@PathVariable String id,
                                             @Valid @RequestBody QueueUpdateRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping("/api/v1/application-queue/items/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/v1/application-queue/items/{id}:approve")
    public ApiResponse<QueueItemView> approve(@PathVariable String id,
                                              @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success(service.approve(CurrentUser.require().userId(), id, request));
    }

    @PostMapping("/api/v1/application-queue/items/{id}:skip")
    public ApiResponse<QueueItemView> skip(@PathVariable String id,
                                           @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success(service.skip(CurrentUser.require().userId(), id, request));
    }

    @PostMapping("/api/v1/application-queue/items/{id}:prepare")
    public ApiResponse<AssistPrepareView> prepare(@PathVariable String id,
                                                  @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success(service.prepare(CurrentUser.require().userId(), id, request));
    }
}

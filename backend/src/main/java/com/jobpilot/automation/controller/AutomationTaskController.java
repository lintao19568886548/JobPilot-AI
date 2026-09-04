package com.jobpilot.automation.controller;

import static com.jobpilot.automation.dto.AutomationDtos.AutomationTaskView;

import com.jobpilot.automation.service.AutomationTaskService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutomationTaskController {
    private final AutomationTaskService service;

    public AutomationTaskController(AutomationTaskService service) { this.service = service; }

    @PostMapping("/api/v1/extension/queue/{queueId}:prepare")
    public ResponseEntity<ApiResponse<AutomationTaskView>> prepare(
            @PathVariable String queueId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var principal = CurrentUser.require();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(service.prepare(
                principal.userId(), CurrentUser.requireExtensionDeviceId(), queueId, idempotencyKey)));
    }

    @GetMapping("/api/v1/extension/automation-tasks/{id}")
    public ApiResponse<AutomationTaskView> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }
}

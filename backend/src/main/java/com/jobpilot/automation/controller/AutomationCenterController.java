package com.jobpilot.automation.controller;

import static com.jobpilot.automation.dto.AutomationCenterDtos.*;

import com.jobpilot.automation.service.AutomationAuthorizationService;
import com.jobpilot.automation.service.AutomationCenterService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/automation-center")
public class AutomationCenterController {
    private final AutomationCenterService center;
    private final AutomationAuthorizationService authorizations;

    public AutomationCenterController(AutomationCenterService center,
                                      AutomationAuthorizationService authorizations) {
        this.center = center;
        this.authorizations = authorizations;
    }

    @GetMapping("/dashboard") public ApiResponse<AutomationDashboard> dashboard() { return ApiResponse.success(center.dashboard(userId())); }
    @PostMapping("/rules") public ApiResponse<RuleView> createRule(@Valid @RequestBody RuleCreateRequest request) { return ApiResponse.success(center.createRule(userId(), request)); }
    @GetMapping("/rules") public ApiResponse<List<RuleView>> rules() { return ApiResponse.success(center.listRules(userId())); }
    @PutMapping("/rules/{id}") public ApiResponse<RuleView> updateRule(@PathVariable String id, @Valid @RequestBody RuleUpdateRequest request) { return ApiResponse.success(center.updateRule(userId(), id, request)); }
    @PostMapping("/rules/{id}:run") public ApiResponse<TaskView> runRule(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody(required = false) RuleRunRequest request) { return ApiResponse.success(center.runRule(userId(), id, key, request)); }
    @GetMapping("/tasks") public ApiResponse<List<TaskView>> tasks(@RequestParam(required = false) String status) { return ApiResponse.success(center.listTasks(userId(), status)); }
    @GetMapping("/tasks/{id}") public ApiResponse<TaskView> task(@PathVariable String id) { return ApiResponse.success(center.task(userId(), id)); }
    @PostMapping("/tasks/{id}:retry") public ApiResponse<TaskView> retry(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(center.retry(userId(), id, request.version())); }
    @PostMapping("/tasks/{id}:cancel") public ApiResponse<TaskView> cancel(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(center.cancel(userId(), id, request.version())); }
    @GetMapping("/suggestions") public ApiResponse<List<SuggestionView>> suggestions(@RequestParam(required = false) String status) { return ApiResponse.success(center.listSuggestions(userId(), status)); }
    @PostMapping("/suggestions/{id}:accept") public ApiResponse<SuggestionView> acceptSuggestion(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(center.decideSuggestion(userId(), id, request.version(), "ACCEPTED")); }
    @PostMapping("/suggestions/{id}:dismiss") public ApiResponse<SuggestionView> dismissSuggestion(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(center.decideSuggestion(userId(), id, request.version(), "DISMISSED")); }
    @GetMapping("/notifications") public ApiResponse<List<NotificationView>> notifications(@RequestParam(required = false) String status) { return ApiResponse.success(center.listNotifications(userId(), status)); }
    @PostMapping("/notifications/{id}:read") public ApiResponse<NotificationView> readNotification(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(center.readNotification(userId(), id, request.version())); }
    @PostMapping("/authorizations") public ApiResponse<AuthorizationView> grantAuthorization(@Valid @RequestBody AuthorizationRequest request) { return ApiResponse.success(authorizations.grant(userId(), request)); }
    @GetMapping("/authorizations") public ApiResponse<List<AuthorizationView>> authorizations() { return ApiResponse.success(authorizations.list(userId())); }
    @DeleteMapping("/authorizations/{id}") public ApiResponse<AuthorizationView> revokeAuthorization(@PathVariable String id) { return ApiResponse.success(authorizations.revoke(userId(), id)); }
    @GetMapping("/policy-decision") public ApiResponse<PolicyDecisionView> policyDecision(@RequestParam String platform, @RequestParam String scope) { return ApiResponse.success(authorizations.decision(userId(), platform, scope)); }

    private static Long userId() { return CurrentUser.require().userId(); }
}

package com.jobpilot.operations.controller;

import static com.jobpilot.operations.dto.OperationsDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.operations.service.OperationsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;

    public OperationsController(OperationsService service) { this.service = service; }

    @GetMapping("/overview") public ApiResponse<OverviewView> overview() { return ApiResponse.success(service.overview(userId())); }
    @GetMapping("/ai-usage") public ApiResponse<UsageView> usage() { return ApiResponse.success(service.usage(userId())); }
    @GetMapping("/ai-budget") public ApiResponse<BudgetView> budget() { return ApiResponse.success(service.budget(userId())); }
    @PutMapping("/ai-budget") public ApiResponse<BudgetView> updateBudget(@Valid @RequestBody BudgetRequest request) { return ApiResponse.success(service.updateBudget(userId(), request)); }
    @GetMapping("/runs") public ApiResponse<List<RunView>> runs(@RequestParam(required=false) String type) { return ApiResponse.success(service.runs(userId(), type)); }
    @GetMapping("/runs/{id}") public ApiResponse<RunView> run(@PathVariable String id) { return ApiResponse.success(service.run(userId(), id)); }
    @PostMapping("/runs") public ApiResponse<RunView> createRun(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody RunRequest request) { return ApiResponse.success(service.createRun(userId(), key, request)); }

    private static Long userId() { return CurrentUser.require().userId(); }
}

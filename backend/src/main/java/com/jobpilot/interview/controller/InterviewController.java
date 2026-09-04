package com.jobpilot.interview.controller;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.interview.service.InterviewDashboardService;
import com.jobpilot.interview.service.InterviewService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterviewController {
    private final InterviewService service;
    private final InterviewDashboardService dashboard;
    public InterviewController(InterviewService service, InterviewDashboardService dashboard) { this.service = service; this.dashboard = dashboard; }

    @GetMapping("/api/v1/interviews")
    public ApiResponse<InterviewPage> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String company, @RequestParam(required = false) String role,
            @RequestParam(required = false) String jobId, @RequestParam(required = false) String applicationId) {
        return ApiResponse.success(service.list(CurrentUser.require().userId(), page, size, status, utc(from), utc(to), company, role, jobId, applicationId));
    }
    @PostMapping("/api/v1/interviews") public ApiResponse<InterviewView> create(@Valid @RequestBody InterviewCreateRequest request) { return ApiResponse.success(service.create(CurrentUser.require().userId(), request)); }
    @GetMapping("/api/v1/interviews/{id}") public ApiResponse<InterviewView> get(@PathVariable String id) { return ApiResponse.success(service.get(CurrentUser.require().userId(), id)); }
    @PutMapping("/api/v1/interviews/{id}") public ApiResponse<InterviewView> update(@PathVariable String id, @Valid @RequestBody InterviewUpdateRequest request) { return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping("/api/v1/interviews/{id}") public ApiResponse<Void> delete(@PathVariable String id) { service.delete(CurrentUser.require().userId(), id); return ApiResponse.success("deleted", null); }
    @PostMapping("/api/v1/interviews/{id}/rounds") public ApiResponse<RoundView> createRound(@PathVariable String id, @Valid @RequestBody RoundCreateRequest request) { return ApiResponse.success(service.createRound(CurrentUser.require().userId(), id, request)); }
    @PutMapping("/api/v1/interview-rounds/{id}") public ApiResponse<RoundView> updateRound(@PathVariable String id, @Valid @RequestBody RoundUpdateRequest request) { return ApiResponse.success(service.updateRound(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping("/api/v1/interview-rounds/{id}") public ApiResponse<Void> deleteRound(@PathVariable String id) { service.deleteRound(CurrentUser.require().userId(), id); return ApiResponse.success("deleted", null); }
    @GetMapping("/api/v1/interviews/dashboard") public ApiResponse<InterviewDashboardView> dashboard() { return ApiResponse.success(dashboard.get(CurrentUser.require().userId())); }
    private static LocalDateTime utc(OffsetDateTime value) { return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC); }
}

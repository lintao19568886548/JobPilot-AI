package com.jobpilot.interview.controller;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.interview.service.InterviewReminderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterviewReminderController {
    private final InterviewReminderService service;
    public InterviewReminderController(InterviewReminderService service) { this.service = service; }
    @GetMapping("/api/v1/interview-reminders") public ApiResponse<List<ReminderView>> list(@RequestParam(required = false) String status) { return ApiResponse.success(service.list(CurrentUser.require().userId(), status)); }
    @PostMapping("/api/v1/interview-reminders") public ApiResponse<ReminderView> create(@Valid @RequestBody ReminderRequest request) { return ApiResponse.success(service.create(CurrentUser.require().userId(), request)); }
    @PutMapping("/api/v1/interview-reminders/{id}") public ApiResponse<ReminderView> update(@PathVariable String id, @Valid @RequestBody ReminderUpdateRequest request) { return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request)); }
    @PostMapping("/api/v1/interview-reminders/{id}:done") public ApiResponse<ReminderView> done(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.done(CurrentUser.require().userId(), id, request)); }
    @PostMapping("/api/v1/interview-reminders/{id}:cancel") public ApiResponse<ReminderView> cancel(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.cancel(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping("/api/v1/interview-reminders/{id}") public ApiResponse<Void> delete(@PathVariable String id) { service.delete(CurrentUser.require().userId(), id); return ApiResponse.success("deleted", null); }
}

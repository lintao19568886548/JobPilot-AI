package com.jobpilot.interview.controller;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.interview.service.InterviewAgentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterviewReviewController {
    private final InterviewAgentService service;
    public InterviewReviewController(InterviewAgentService service) { this.service = service; }
    @PostMapping("/api/v1/interviews/{id}/reviews:generate") public ApiResponse<ReviewView> generate(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(service.generateReview(CurrentUser.require().userId(), id, key)); }
    @GetMapping("/api/v1/interviews/{id}/reviews") public ApiResponse<List<ReviewView>> list(@PathVariable String id) { return ApiResponse.success(service.listReviews(CurrentUser.require().userId(), id)); }
    @PostMapping("/api/v1/interview-reviews/{id}:confirm") public ApiResponse<ReviewView> confirm(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.confirmReview(CurrentUser.require().userId(), id, request)); }
}

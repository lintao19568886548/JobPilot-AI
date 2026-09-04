package com.jobpilot.matching.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.matching.dto.MatchingDtos.JobMatchView;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunRequest;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunView;
import com.jobpilot.matching.service.MatchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingController {
    private final MatchService service;
    public MatchingController(MatchService service){this.service=service;}
    @PostMapping({"/api/jobs/{jobId}/match-runs","/api/v1/jobs/{jobId}/match-runs"})
    public ResponseEntity<ApiResponse<MatchRunView>> start(@PathVariable String jobId,@Valid @RequestBody MatchRunRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey){return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(service.start(CurrentUser.require().userId(),jobId,request,idempotencyKey)));}
    @GetMapping({"/api/match-runs/{id}","/api/v1/match-runs/{id}"})
    public ApiResponse<MatchRunView> run(@PathVariable String id){return ApiResponse.success(service.getRun(CurrentUser.require().userId(),id));}
    @PostMapping({"/api/match-runs/{id}:retry","/api/v1/match-runs/{id}:retry"})
    public ResponseEntity<ApiResponse<MatchRunView>> retry(@PathVariable String id){return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(service.retry(CurrentUser.require().userId(),id)));}
    @GetMapping({"/api/jobs/{jobId}/matches","/api/v1/jobs/{jobId}/matches"})
    public ApiResponse<List<JobMatchView>> matches(@PathVariable String jobId){return ApiResponse.success(service.listMatches(CurrentUser.require().userId(),jobId));}
    @GetMapping({"/api/job-matches/{id}","/api/v1/job-matches/{id}"})
    public ApiResponse<JobMatchView> match(@PathVariable String id){return ApiResponse.success(service.getMatch(CurrentUser.require().userId(),id));}
}

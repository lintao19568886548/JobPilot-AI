package com.jobpilot.job.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.job.dto.JobDtos.CreateResult;
import com.jobpilot.job.dto.JobDtos.JobCreateRequest;
import com.jobpilot.job.dto.JobDtos.JobDetailView;
import com.jobpilot.job.dto.JobDtos.JobPageView;
import com.jobpilot.job.dto.JobDtos.JobQuery;
import com.jobpilot.job.dto.JobDtos.JobUpdateRequest;
import com.jobpilot.job.dto.JobDtos.SourceRequest;
import com.jobpilot.job.dto.JobDtos.SourceView;
import com.jobpilot.job.service.JobService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {
    private final JobService service;
    public JobController(JobService service) { this.service = service; }

    @PostMapping({"/api/jobs", "/api/v1/jobs"})
    public ApiResponse<CreateResult> create(@Valid @RequestBody JobCreateRequest request) { return ApiResponse.success(service.create(CurrentUser.require().userId(), request)); }

    @GetMapping({"/api/jobs", "/api/v1/jobs"})
    public ApiResponse<JobPageView> list(
            @RequestParam(required=false) String cursor, @RequestParam(required=false) Integer limit,
            @RequestParam(required=false) String title, @RequestParam(required=false) String city,
            @RequestParam(required=false) BigDecimal salaryMin, @RequestParam(required=false) BigDecimal salaryMax,
            @RequestParam(required=false) String education, @RequestParam(required=false) BigDecimal experienceMin,
            @RequestParam(required=false) BigDecimal experienceMax, @RequestParam(required=false) String company,
            @RequestParam(required=false) String industry, @RequestParam(required=false) String platform,
            @RequestParam(required=false) String skill, @RequestParam(required=false) String companySize,
            @RequestParam(required=false) String status, @RequestParam(required=false) String parseStatus,
            @RequestParam(required=false) String sort,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishTo) {
        return ApiResponse.success(service.list(CurrentUser.require().userId(), new JobQuery(cursor,limit,title,city,salaryMin,salaryMax,education,experienceMin,experienceMax,company,industry,platform,skill,companySize,status,parseStatus,sort,publishFrom,publishTo)));
    }

    @GetMapping({"/api/jobs/{id}", "/api/v1/jobs/{id}"})
    public ApiResponse<JobDetailView> get(@PathVariable String id) { return ApiResponse.success(service.get(CurrentUser.require().userId(), id)); }
    @PatchMapping({"/api/jobs/{id}", "/api/v1/jobs/{id}"})
    public ApiResponse<JobDetailView> update(@PathVariable String id, @Valid @RequestBody JobUpdateRequest request) { return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping({"/api/jobs/{id}", "/api/v1/jobs/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) { service.delete(CurrentUser.require().userId(), id); return ApiResponse.success(null); }
    @PostMapping({"/api/jobs/{id}/parse-runs", "/api/v1/jobs/{id}/parse-runs"})
    public ApiResponse<JobDetailView> parse(@PathVariable String id) { return ApiResponse.success(service.parse(CurrentUser.require().userId(), id)); }
    @GetMapping({"/api/jobs/{id}/sources", "/api/v1/jobs/{id}/sources"})
    public ApiResponse<List<SourceView>> sources(@PathVariable String id) { return ApiResponse.success(service.sources(CurrentUser.require().userId(), id)); }
    @PostMapping({"/api/jobs/{id}/sources", "/api/v1/jobs/{id}/sources"})
    public ApiResponse<SourceView> addSource(@PathVariable String id, @Valid @RequestBody SourceRequest request) { return ApiResponse.success(service.addSource(CurrentUser.require().userId(), id, request)); }
    @PostMapping({"/api/jobs/{id}:ignore", "/api/v1/jobs/{id}:ignore"})
    public ApiResponse<JobDetailView> ignore(@PathVariable String id) { return ApiResponse.success(service.ignore(CurrentUser.require().userId(), id)); }
    @PostMapping({"/api/jobs/{id}:restore", "/api/v1/jobs/{id}:restore"})
    public ApiResponse<JobDetailView> restore(@PathVariable String id) { return ApiResponse.success(service.restore(CurrentUser.require().userId(), id)); }
}

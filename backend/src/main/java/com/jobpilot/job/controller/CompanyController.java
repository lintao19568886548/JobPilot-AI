package com.jobpilot.job.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.job.dto.JobDtos.CompanyRequest;
import com.jobpilot.job.dto.JobDtos.CompanyUpdateRequest;
import com.jobpilot.job.dto.JobDtos.CompanyView;
import com.jobpilot.job.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompanyController {
    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service = service; }

    @GetMapping({"/api/companies", "/api/v1/companies"})
    public ApiResponse<List<CompanyView>> list(@RequestParam(required = false) String search) { return ApiResponse.success(service.list(search)); }
    @GetMapping({"/api/companies/{id}", "/api/v1/companies/{id}"})
    public ApiResponse<CompanyView> get(@PathVariable String id) { return ApiResponse.success(service.get(id)); }
    @PostMapping({"/api/companies", "/api/v1/companies"})
    public ApiResponse<CompanyView> create(@Valid @RequestBody CompanyRequest request) { return ApiResponse.success(service.create(CurrentUser.require().userId(), request)); }
    @PatchMapping({"/api/companies/{id}", "/api/v1/companies/{id}"})
    public ApiResponse<CompanyView> update(@PathVariable String id, @Valid @RequestBody CompanyUpdateRequest request) { return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping({"/api/companies/{id}", "/api/v1/companies/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) { service.delete(CurrentUser.require().userId(), id); return ApiResponse.success(null); }
}

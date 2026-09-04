package com.jobpilot.search.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.search.dto.SearchDtos.GlobalSearchResult;
import com.jobpilot.search.service.GlobalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalSearchController {
    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping({"/api/search", "/api/v1/search"})
    public ApiResponse<GlobalSearchResult> search(
            @RequestParam String q,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(service.search(CurrentUser.require().userId(), q, types, limit));
    }
}

package com.jobpilot.matching.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterRuleRequest;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterRuleView;
import com.jobpilot.matching.service.HardFilterService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HardFilterController {
    private final HardFilterService service;
    public HardFilterController(HardFilterService service){this.service=service;}
    @GetMapping({"/api/hard-filter-rules","/api/v1/hard-filter-rules"})
    public ApiResponse<List<HardFilterRuleView>> list(){return ApiResponse.success(service.list(CurrentUser.require().userId()));}
    @PostMapping({"/api/hard-filter-rules","/api/v1/hard-filter-rules"})
    public ApiResponse<HardFilterRuleView> create(@Valid @RequestBody HardFilterRuleRequest request){return ApiResponse.success(service.create(CurrentUser.require().userId(),request));}
    @PatchMapping({"/api/hard-filter-rules/{id}","/api/v1/hard-filter-rules/{id}"})
    public ApiResponse<HardFilterRuleView> update(@PathVariable String id,@Valid @RequestBody HardFilterRuleRequest request){return ApiResponse.success(service.update(CurrentUser.require().userId(),id,request));}
}

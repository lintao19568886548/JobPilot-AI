package com.jobpilot.matching.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.matching.dto.MatchingDtos.MatchConfigRequest;
import com.jobpilot.matching.dto.MatchingDtos.MatchConfigView;
import com.jobpilot.matching.service.MatchConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchConfigController {
    private final MatchConfigService service;
    public MatchConfigController(MatchConfigService service){this.service=service;}
    @GetMapping({"/api/match-configs","/api/v1/match-configs"})
    public ApiResponse<List<MatchConfigView>> list(){return ApiResponse.success(service.list(CurrentUser.require().userId()));}
    @PostMapping({"/api/match-configs","/api/v1/match-configs"})
    public ApiResponse<MatchConfigView> create(@Valid @RequestBody MatchConfigRequest request){return ApiResponse.success(service.create(CurrentUser.require().userId(),request));}
    @PostMapping({"/api/match-configs/{id}:activate","/api/v1/match-configs/{id}:activate"})
    public ApiResponse<MatchConfigView> activate(@PathVariable String id){return ApiResponse.success(service.activate(CurrentUser.require().userId(),id));}
}

package com.jobpilot.interview.controller;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.interview.service.KnowledgeGapService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class KnowledgeGapController {
    private final KnowledgeGapService service;
    public KnowledgeGapController(KnowledgeGapService service) { this.service = service; }
    @GetMapping("/api/v1/knowledge-gaps") public ApiResponse<List<KnowledgeGapView>> list(@RequestParam(required = false) String status) { return ApiResponse.success(service.list(CurrentUser.require().userId(), status)); }
    @PostMapping("/api/v1/knowledge-gaps/{id}:activate") public ApiResponse<KnowledgeGapView> activate(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.activate(CurrentUser.require().userId(), id, request)); }
    @PostMapping("/api/v1/knowledge-gaps/{id}:resolve") public ApiResponse<KnowledgeGapView> resolve(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.resolve(CurrentUser.require().userId(), id, request)); }
    @PostMapping("/api/v1/knowledge-gaps/{id}:dismiss") public ApiResponse<KnowledgeGapView> dismiss(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(service.dismiss(CurrentUser.require().userId(), id, request)); }
}

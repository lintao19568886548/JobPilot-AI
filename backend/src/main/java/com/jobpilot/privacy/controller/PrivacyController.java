package com.jobpilot.privacy.controller;

import static com.jobpilot.privacy.dto.PrivacyDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.privacy.service.PrivacyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {
    private final PrivacyService service;
    public PrivacyController(PrivacyService service){this.service=service;}
    @GetMapping("/export") public ApiResponse<ExportView> export(){return ApiResponse.success(service.export(CurrentUser.require().userId()));}
    @PostMapping({"/delete:preview","/deletions:preview"}) public ApiResponse<DeletionPreviewView> preview(@RequestHeader("Idempotency-Key") String key){return ApiResponse.success(service.preview(CurrentUser.require().userId(),key));}
    @PostMapping({"/delete:confirm","/deletions:confirm"}) public ApiResponse<DeletionResultView> confirm(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody DeletionConfirmRequest request){return ApiResponse.success(service.confirm(CurrentUser.require().userId(),key,request));}
}

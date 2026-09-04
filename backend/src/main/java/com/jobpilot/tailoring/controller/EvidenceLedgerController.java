package com.jobpilot.tailoring.controller;

import static com.jobpilot.tailoring.dto.TailoringDtos.EvidenceLedgerView;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.tailoring.service.EvidenceLedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceLedgerController {
    private final EvidenceLedgerService service;
    public EvidenceLedgerController(EvidenceLedgerService service) { this.service = service; }

    @GetMapping("/api/v1/evidence-ledger")
    public ApiResponse<EvidenceLedgerView> get() { return ApiResponse.success(service.current(CurrentUser.require().userId())); }

    @PostMapping("/api/v1/evidence-ledger:refresh")
    public ApiResponse<EvidenceLedgerView> refresh() { return ApiResponse.success(service.refresh(CurrentUser.require().userId())); }
}

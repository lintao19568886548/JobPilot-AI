package com.jobpilot.extension.controller;

import static com.jobpilot.extension.dto.ExtensionDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.extension.service.ExtensionPairingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExtensionPairingController {
    private final ExtensionPairingService service;
    public ExtensionPairingController(ExtensionPairingService service) { this.service = service; }

    @PostMapping("/api/v1/extension/pairing-codes")
    public ApiResponse<PairingCodeView> code() {
        return ApiResponse.success(service.createCode(CurrentUser.require().userId()));
    }

    @PostMapping("/api/v1/extension/pairings")
    public ApiResponse<ExtensionTokensView> pair(@Valid @RequestBody PairingRequest request) {
        return ApiResponse.success(service.pair(request));
    }

    @PostMapping("/api/v1/extension/tokens/refresh")
    public ApiResponse<ExtensionTokensView> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(service.refresh(request));
    }

    @GetMapping("/api/v1/extension/devices")
    public ApiResponse<List<ExtensionDeviceView>> devices() {
        return ApiResponse.success(service.list(CurrentUser.require().userId()));
    }

    @DeleteMapping("/api/v1/extension/devices/{id}")
    public ApiResponse<Void> revoke(@PathVariable String id) {
        service.revoke(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }
}

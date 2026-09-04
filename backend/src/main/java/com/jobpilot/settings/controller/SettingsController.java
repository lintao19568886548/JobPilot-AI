package com.jobpilot.settings.controller;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.settings.dto.SettingsDtos.AccountUpdateRequest;
import com.jobpilot.settings.dto.SettingsDtos.AccountView;
import com.jobpilot.settings.dto.SettingsDtos.SettingUpdateRequest;
import com.jobpilot.settings.dto.SettingsDtos.SettingView;
import com.jobpilot.settings.dto.SettingsDtos.SettingsOverview;
import com.jobpilot.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiResponse<SettingsOverview> overview() {
        return ApiResponse.success(settings.overview(CurrentUser.require().userId()));
    }

    @PutMapping("/account")
    public ApiResponse<AccountView> updateAccount(@Valid @RequestBody AccountUpdateRequest request) {
        return ApiResponse.success(settings.updateAccount(CurrentUser.require().userId(), request));
    }

    @PutMapping("/{group}/{key}")
    public ApiResponse<SettingView> updateSetting(
            @PathVariable String group,
            @PathVariable String key,
            @Valid @RequestBody SettingUpdateRequest request) {
        return ApiResponse.success(settings.updateSetting(CurrentUser.require().userId(), group, key, request));
    }
}

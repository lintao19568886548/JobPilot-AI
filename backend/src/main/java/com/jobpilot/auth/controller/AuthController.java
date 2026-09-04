package com.jobpilot.auth.controller;

import com.jobpilot.auth.dto.AuthResponse;
import com.jobpilot.auth.dto.LoginRequest;
import com.jobpilot.auth.dto.RefreshRequest;
import com.jobpilot.auth.dto.PasswordChangeRequest;
import com.jobpilot.auth.dto.PasswordChangeResponse;
import com.jobpilot.auth.dto.SessionView;
import com.jobpilot.auth.dto.UserView;
import com.jobpilot.auth.service.AuthService;
import com.jobpilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import com.jobpilot.common.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, authService.clientContext(
                servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr())));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.refresh(request.refreshToken(), authService.clientContext(
                servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @PostMapping("/password")
    public ApiResponse<PasswordChangeResponse> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return ApiResponse.success(authService.changePassword(CurrentUser.require().userId(), request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionView>> sessions() {
        var principal = CurrentUser.require();
        return ApiResponse.success(authService.sessions(principal.userId(), principal.sessionFamilyId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@org.springframework.web.bind.annotation.PathVariable String sessionId) {
        authService.revokeSession(CurrentUser.require().userId(), sessionId);
        return ApiResponse.success(null);
    }
}

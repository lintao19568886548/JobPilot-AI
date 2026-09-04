package com.jobpilot.common.security;

import com.jobpilot.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static JobPilotPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JobPilotPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    public static String requireExtensionDeviceId() {
        JobPilotPrincipal principal = require();
        if (!"EXTENSION".equals(principal.clientType()) || principal.extensionDeviceId() == null) {
            throw new UnauthorizedException("Extension device authentication required");
        }
        return principal.extensionDeviceId();
    }
}

package com.jobpilot.common.security;

public record JobPilotPrincipal(Long userId, String publicId, String username,
                                String clientType, String extensionDeviceId, String sessionFamilyId) {
    public JobPilotPrincipal(Long userId, String publicId, String username) {
        this(userId, publicId, username, "WEB", null, null);
    }

    public JobPilotPrincipal(Long userId, String publicId, String username,
                             String clientType, String extensionDeviceId) {
        this(userId, publicId, username, clientType, extensionDeviceId, null);
    }
}

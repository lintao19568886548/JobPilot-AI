package com.jobpilot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("test-secret-that-is-at-least-thirty-two-characters-long");
        properties.setIssuer("jobpilot-test");
        properties.setAccessExpirationSeconds(900);
        properties.setRefreshExpirationSeconds(3600);
        jwtService = new JwtService(properties);
        user = new UserEntity();
        user.setId(42L);
        user.setPublicId("01K45X9J9B0000000000000042");
        user.setUsername("tester");
        user.setAuthVersion(4);
    }

    @Test
    void createsAndParsesAccessToken() {
        Claims claims = jwtService.parseAndRequireType(jwtService.createAccessToken(user, "family-web-1"), JwtService.ACCESS);
        assertThat(jwtService.userId(claims)).isEqualTo(42L);
        assertThat(jwtService.userAuthVersion(claims)).isEqualTo(4);
        assertThat(jwtService.familyId(claims)).isEqualTo("family-web-1");
        assertThat(claims.getSubject()).isEqualTo(user.getPublicId());
    }

    @Test
    void createsRefreshTokenWithFamily() {
        Claims claims = jwtService.parseAndRequireType(jwtService.createRefreshToken(user, "family-1"), JwtService.REFRESH);
        assertThat(claims.get("family", String.class)).isEqualTo("family-1");
    }

    @Test
    void createsDeviceBoundExtensionAccessToken() {
        String token = jwtService.createExtensionAccessToken(user, "01M1DEVICE0000000000000000", 3,
                List.of("JOB_CAPTURE", "ASSIST_PREPARE"), 300);
        Claims claims = jwtService.parseAndRequireType(token, JwtService.EXTENSION_ACCESS);
        assertThat(jwtService.userId(claims)).isEqualTo(42L);
        assertThat(jwtService.extensionDeviceId(claims)).isEqualTo("01M1DEVICE0000000000000000");
        assertThat(jwtService.extensionTokenVersion(claims)).isEqualTo(3);
        assertThat(claims.get("scopes", List.class)).containsExactly("JOB_CAPTURE", "ASSIST_PREPARE");
    }

    @Test
    void rejectsWrongTokenType() {
        String access = jwtService.createAccessToken(user);
        assertThatThrownBy(() -> jwtService.parseAndRequireType(access, JwtService.REFRESH))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void createsStableUrlSafeDigest() {
        assertThat(jwtService.digest("token-value"))
                .hasSize(43)
                .isEqualTo(jwtService.digest("token-value"));
    }

    @Test
    void refusesWeakSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("too-short");
        assertThatThrownBy(() -> new JwtService(properties)).isInstanceOf(IllegalStateException.class);
    }
}

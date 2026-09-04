package com.jobpilot.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.common.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigurationTest {

    private static final String EXTENSION_ORIGIN = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";
    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void allowsChromiumExtensionOriginOnlyOnExtensionApi() {
        CorsConfigurationSource source = source();

        CorsConfiguration extension = source.getCorsConfiguration(request("/api/v1/extension/pairings"));
        CorsConfiguration ordinary = source.getCorsConfiguration(request("/api/v1/auth/me"));

        assertThat(extension).isNotNull();
        assertThat(extension.checkOrigin(EXTENSION_ORIGIN)).isEqualTo(EXTENSION_ORIGIN);
        assertThat(extension.getAllowCredentials()).isFalse();
        assertThat(ordinary).isNotNull();
        assertThat(ordinary.checkOrigin(EXTENSION_ORIGIN)).isNull();
    }

    @Test
    void keepsExplicitFrontendOriginsAvailableOnBothApiSurfaces() {
        CorsConfigurationSource source = source();
        String frontend = "http://127.0.0.1:5173";

        assertThat(source.getCorsConfiguration(request("/api/v1/extension/pairing-codes")).checkOrigin(frontend))
                .isEqualTo(frontend);
        assertThat(source.getCorsConfiguration(request("/api/v1/auth/me")).checkOrigin(frontend))
                .isEqualTo(frontend);
    }

    private CorsConfigurationSource source() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("http://localhost:5173,http://127.0.0.1:5173");
        properties.setExtensionAllowedOriginPatterns("chrome-extension://*");
        return securityConfig.corsConfigurationSource(properties);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader(HttpHeaders.ORIGIN, EXTENSION_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        return request;
    }
}

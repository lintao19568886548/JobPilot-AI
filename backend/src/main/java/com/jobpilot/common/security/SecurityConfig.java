package com.jobpilot.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.config.BootstrapProperties;
import com.jobpilot.common.config.CorsProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import com.jobpilot.extension.security.ExtensionAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, BootstrapProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            ExtensionAuthenticationFilter extensionFilter,
            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login", "/api/auth/refresh",
                                "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/actuator/health", "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/extension/pairings", "/api/v1/extension/tokens/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/extension/pairing-codes").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/extension/devices").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/extension/devices/**").hasRole("USER")
                        .requestMatchers("/api/v1/extension/**").hasAuthority("SCOPE_EXTENSION")
                        .anyRequest().hasRole("USER"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(4010001, "Authentication required"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(4030001, "Forbidden"));
                        }))
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> { })
                        .frameOptions(frame -> frame.deny()))
                .addFilterBefore(extensionFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration application = baseCorsConfiguration();
        application.setAllowedOrigins(properties.origins());

        CorsConfiguration extension = baseCorsConfiguration();
        extension.setAllowedOrigins(properties.origins());
        extension.setAllowedOriginPatterns(properties.extensionOriginPatterns());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/extension/**", extension);
        source.registerCorsConfiguration("/**", application);
        return source;
    }

    private CorsConfiguration baseCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "If-Match", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        return configuration;
    }
}

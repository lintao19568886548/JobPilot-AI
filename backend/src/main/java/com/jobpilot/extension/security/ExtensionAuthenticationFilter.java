package com.jobpilot.extension.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.auth.service.JwtService;
import com.jobpilot.common.security.JobPilotPrincipal;
import com.jobpilot.extension.domain.ExtensionDeviceEntity;
import com.jobpilot.extension.mapper.ExtensionDeviceMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ExtensionAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final ExtensionDeviceMapper devices;
    private final UserMapper users;

    public ExtensionAuthenticationFilter(JwtService jwt, ExtensionDeviceMapper devices, UserMapper users) {
        this.jwt = jwt; this.devices = devices; this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/extension/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer jpe_")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwt.parseAndRequireType(header.substring("Bearer jpe_".length()), JwtService.EXTENSION_ACCESS);
                long userId = jwt.userId(claims);
                String deviceId = jwt.extensionDeviceId(claims);
                int tokenVersion = jwt.extensionTokenVersion(claims);
                ExtensionDeviceEntity device = devices.selectOne(new LambdaQueryWrapper<ExtensionDeviceEntity>()
                        .eq(ExtensionDeviceEntity::getPublicId, deviceId)
                        .eq(ExtensionDeviceEntity::getUserId, userId).last("LIMIT 1"));
                UserEntity user = users.selectById(userId);
                if (device != null && user != null && "ACTIVE".equals(device.getStatus())
                        && tokenVersion == device.getTokenVersion()
                        && jwt.userAuthVersion(claims) == (user.getAuthVersion() == null ? 0 : user.getAuthVersion())
                        && "ACTIVE".equals(user.getStatus())) {
                    device.setLastSeenAt(LocalDateTime.now());
                    devices.updateById(device);
                    JobPilotPrincipal principal = new JobPilotPrincipal(userId, user.getPublicId(), user.getUsername(),
                            "EXTENSION", device.getPublicId());
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("SCOPE_EXTENSION")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

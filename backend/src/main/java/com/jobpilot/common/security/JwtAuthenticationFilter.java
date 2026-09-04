package com.jobpilot.common.security;

import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper, StringRedisTemplate redisTemplate) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseAndRequireType(header.substring(7), JwtService.ACCESS);
                UserEntity user = userMapper.selectById(jwtService.userId(claims));
                String familyId = jwtService.familyId(claims);
                boolean familyRevoked = Boolean.TRUE.equals(redisTemplate.hasKey("jobpilot:auth:family-revoked:" + familyId));
                int authVersion = user == null || user.getAuthVersion() == null ? 0 : user.getAuthVersion();
                if (user != null && "ACTIVE".equals(user.getStatus())
                        && jwtService.userAuthVersion(claims) == authVersion && !familyRevoked) {
                    JobPilotPrincipal principal = new JobPilotPrincipal(
                            user.getId(), user.getPublicId(), user.getUsername(), "WEB", null, familyId);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}

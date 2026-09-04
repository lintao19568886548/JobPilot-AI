package com.jobpilot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.RefreshTokenEntity;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.dto.LoginRequest;
import com.jobpilot.auth.mapper.RefreshTokenMapper;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserMapper userMapper;
    private RefreshTokenMapper refreshTokenMapper;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private AuditService audit;
    private JwtService jwt;
    private AuthService service;
    private UserEntity user;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        refreshTokenMapper = mock(RefreshTokenMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        audit = mock(AuditService.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("auth-test-secret-that-is-at-least-thirty-two-characters");
        properties.setIssuer("jobpilot-test");
        properties.setAccessExpirationSeconds(900);
        properties.setRefreshExpirationSeconds(3600);
        jwt = new JwtService(properties);
        service = new AuthService(userMapper, refreshTokenMapper, passwordEncoder, jwt, redis, audit);
        user = new UserEntity();
        user.setId(10L);
        user.setPublicId("01K45X9J9B0000000000000010");
        user.setUsername("local_admin");
        user.setEmail("admin@example.local");
        user.setDisplayName("Local Admin");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        user.setVersion(0);
    }

    @Test
    void loginIssuesDistinctAccessAndRefreshTokensAndCachesRefresh() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(userMapper.updateById(user)).thenReturn(1);
        doAnswer(invocation -> {
            RefreshTokenEntity record = invocation.getArgument(0);
            record.setId(20L);
            record.setPublicId("refresh-1");
            record.setVersion(0);
            return 1;
        }).when(refreshTokenMapper).insert(any(RefreshTokenEntity.class));
        var response = service.login(new LoginRequest("local_admin", "correct-password"));
        assertThat(response.accessToken()).isNotBlank().isNotEqualTo(response.refreshToken());
        assertThat(jwt.parseAndRequireType(response.refreshToken(), JwtService.REFRESH)).isNotNull();
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
        verify(audit).record(10L, "LOGIN", "USER", user.getPublicId());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("local_admin", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsTokenMissingFromRedis() {
        String token = jwt.createRefreshToken(user, "family-1");
        when(valueOperations.get(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.refresh(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRateLimitRejectsRepeatedFailuresBeforePasswordCheck() {
        when(valueOperations.get(anyString())).thenReturn("5");
        assertThatThrownBy(() -> service.login(new LoginRequest("local_admin", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Too many login attempts");
    }
}

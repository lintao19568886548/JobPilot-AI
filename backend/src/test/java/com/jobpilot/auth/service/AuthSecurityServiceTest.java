package com.jobpilot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.RefreshTokenEntity;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.dto.PasswordChangeRequest;
import com.jobpilot.auth.mapper.RefreshTokenMapper;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.security.JwtProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSecurityServiceTest {

    private UserMapper users;
    private RefreshTokenMapper tokens;
    private PasswordEncoder passwords;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AuditService audit;
    private AuthService service;
    private UserEntity user;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        users = mock(UserMapper.class);
        tokens = mock(RefreshTokenMapper.class);
        passwords = mock(PasswordEncoder.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        audit = mock(AuditService.class);
        when(redis.opsForValue()).thenReturn(values);
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("security-test-secret-that-is-long-enough-for-hmac");
        properties.setIssuer("jobpilot-test");
        properties.setAccessExpirationSeconds(900);
        properties.setRefreshExpirationSeconds(3600);
        service = new AuthService(users, tokens, passwords, new JwtService(properties), redis, audit);
        user = new UserEntity();
        user.setId(7L);
        user.setPublicId("01PHASE14USER000000000000");
        user.setUsername("account-user");
        user.setPasswordHash("stored-hash");
        user.setStatus("ACTIVE");
        user.setAuthVersion(2);
        user.setVersion(0);
    }

    @Test
    void passwordChangeRehashesBumpsVersionAndRevokesWebSessions() {
        RefreshTokenEntity token = session("session-1", "family-1");
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("current-password", "stored-hash")).thenReturn(true);
        when(passwords.matches("new-password-123", "stored-hash")).thenReturn(false);
        when(passwords.encode("new-password-123")).thenReturn("new-hash");
        when(users.updateById(user)).thenReturn(1);
        when(tokens.selectList(any())).thenReturn(List.of(token));

        var response = service.changePassword(7L,
                new PasswordChangeRequest("current-password", "new-password-123", "new-password-123"));

        assertThat(response.revokedSessions()).isEqualTo(1);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getAuthVersion()).isEqualTo(3);
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(token.getRevokedAt()).isNotNull();
        verify(audit).record(7L, "PASSWORD_CHANGE", "USER", user.getPublicId());
    }

    @Test
    void passwordChangeRejectsMismatchedConfirmation() {
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("current-password", "stored-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(7L,
                new PasswordChangeRequest("current-password", "new-password-123", "different-password")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("confirmation");
    }

    @Test
    void passwordChangeRejectsWrongCurrentPassword() {
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(7L,
                new PasswordChangeRequest("wrong-password", "new-password-123", "new-password-123")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Current password");
    }

    @Test
    void passwordChangeRejectsWeakPasswordAtServiceBoundary() {
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("current-password", "stored-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(7L,
                new PasswordChangeRequest("current-password", "short", "short")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("12 to 128");
    }

    @Test
    void passwordChangeRejectsReusingCurrentPassword() {
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("current-password", "stored-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(7L,
                new PasswordChangeRequest("current-password", "current-password", "current-password")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different");
    }

    @Test
    void sessionsMarkOnlyTheCurrentFamily() {
        when(tokens.selectList(any())).thenReturn(List.of(
                session("session-1", "family-current"), session("session-2", "family-other")));
        var result = service.sessions(7L, "family-current");
        assertThat(result).hasSize(2);
        assertThat(result).extracting(value -> value.current()).containsExactly(true, false);
    }

    @Test
    void revokeSessionRequiresOwnership() {
        when(tokens.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.revokeSession(7L, "other-user-session"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clientContextStoresOnlyHashedAgentAndMaskedIp() {
        var context = service.clientContext("Mozilla/5.0 Chrome/140.0", "192.168.10.93");
        assertThat(context.clientLabel()).isEqualTo("Google Chrome");
        assertThat(context.userAgentHash()).hasSize(43).doesNotContain("Chrome");
        assertThat(context.ipMasked()).isEqualTo("192.168.10.0");
    }

    private RefreshTokenEntity session(String id, String family) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId((long) id.hashCode());
        token.setPublicId(id);
        token.setUserId(7L);
        token.setFamilyId(family);
        token.setTokenHash("digest-" + id);
        token.setClientType("WEB");
        token.setClientLabel("Web session");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        token.setUpdatedAt(LocalDateTime.now());
        token.setLastUsedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setVersion(0);
        return token;
    }
}

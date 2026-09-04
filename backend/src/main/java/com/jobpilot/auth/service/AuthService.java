package com.jobpilot.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.RefreshTokenEntity;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.dto.AuthResponse;
import com.jobpilot.auth.dto.LoginRequest;
import com.jobpilot.auth.dto.PasswordChangeRequest;
import com.jobpilot.auth.dto.PasswordChangeResponse;
import com.jobpilot.auth.dto.SessionView;
import com.jobpilot.auth.dto.UserView;
import com.jobpilot.auth.mapper.RefreshTokenMapper;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.common.util.UlidGenerator;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String REDIS_PREFIX = "jobpilot:auth:refresh:";
    private static final String FAMILY_REVOKED_PREFIX = "jobpilot:auth:family-revoked:";
    private static final String LOGIN_FAILURE_PREFIX = "jobpilot:auth:login-failure:";
    private static final int MAX_LOGIN_FAILURES = 5;
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            AuditService auditService) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, ClientContext.unknown());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientContext clientContext) {
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        String failureKey = LOGIN_FAILURE_PREFIX + jwtService.digest(login);
        ensureLoginAllowed(failureKey);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .and(wrapper -> wrapper.eq(UserEntity::getUsername, login).or().eq(UserEntity::getEmail, login))
                .last("LIMIT 1"));
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordLoginFailure(failureKey);
            throw new UnauthorizedException("Invalid username/email or password");
        }
        redisTemplate.delete(failureKey);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        AuthResponse response = issueTokenPair(user, UlidGenerator.next(), clientContext);
        auditService.record(user.getId(), "LOGIN", "USER", user.getPublicId());
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        return refresh(rawRefreshToken, ClientContext.unknown());
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, ClientContext clientContext) {
        Claims claims = jwtService.parseAndRequireType(rawRefreshToken, JwtService.REFRESH);
        String familyId = jwtService.familyId(claims);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(FAMILY_REVOKED_PREFIX + familyId))) {
            throw new UnauthorizedException("Refresh token session is revoked");
        }
        String digest = jwtService.digest(rawRefreshToken);
        String redisKey = REDIS_PREFIX + digest;
        String redisUserId = redisTemplate.opsForValue().get(redisKey);
        if (redisUserId == null) {
            throw new UnauthorizedException("Refresh token is revoked or expired");
        }
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, digest)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .gt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 1"));
        if (stored == null) {
            redisTemplate.delete(redisKey);
            throw new UnauthorizedException("Refresh token is revoked or expired");
        }
        Long userId = jwtService.userId(claims);
        if (!stored.getUserId().equals(userId) || !redisUserId.equals(String.valueOf(userId))) {
            throw new UnauthorizedException("Refresh token ownership is invalid");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User is unavailable");
        }
        int authVersion = user.getAuthVersion() == null ? 0 : user.getAuthVersion();
        if (jwtService.userAuthVersion(claims) != authVersion) {
            throw new UnauthorizedException("Refresh token is superseded by a security change");
        }
        redisTemplate.delete(redisKey);
        LocalDateTime now = LocalDateTime.now();
        stored.setLastUsedAt(now);
        stored.setRevokedAt(now);
        ClientContext effectiveContext = clientContext.isUnknown()
                ? new ClientContext(stored.getClientType(), stored.getClientLabel(), stored.getUserAgentHash(), stored.getIpMasked())
                : clientContext;
        AuthResponse response = issueTokenPair(user, stored.getFamilyId(), effectiveContext);
        stored.setReplacedByHash(jwtService.digest(response.refreshToken()));
        refreshTokenMapper.updateById(stored);
        return response;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String digest = jwtService.digest(rawRefreshToken);
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, digest)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .last("LIMIT 1"));
        redisTemplate.delete(REDIS_PREFIX + digest);
        if (stored != null) {
            revokeFamily(stored.getUserId(), stored.getFamilyId());
        }
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(Long userId, String currentFamilyId) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenMapper.selectList(new LambdaQueryWrapper<RefreshTokenEntity>()
                        .eq(RefreshTokenEntity::getUserId, userId)
                        .isNull(RefreshTokenEntity::getRevokedAt)
                        .gt(RefreshTokenEntity::getExpiresAt, now)
                        .orderByDesc(RefreshTokenEntity::getUpdatedAt))
                .stream()
                .map(token -> new SessionView(
                        token.getPublicId(),
                        token.getClientType(),
                        token.getClientLabel(),
                        token.getIpMasked(),
                        Objects.equals(token.getFamilyId(), currentFamilyId),
                        token.getCreatedAt(),
                        token.getLastUsedAt(),
                        token.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, String sessionPublicId) {
        RefreshTokenEntity token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getPublicId, sessionPublicId)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .gt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 1"));
        if (token == null) {
            throw new ResourceNotFoundException("Session");
        }
        revokeFamily(userId, token.getFamilyId());
        auditService.record(userId, "SESSION_REVOKE", "REFRESH_TOKEN", sessionPublicId);
    }

    @Transactional
    public PasswordChangeResponse changePassword(Long userId, PasswordChangeRequest request) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User is unavailable");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        if (request.newPassword().length() < 12 || request.newPassword().length() > 128) {
            throw new ValidationException("New password must contain 12 to 128 characters");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ValidationException("New password confirmation does not match");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ValidationException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setAuthVersion((user.getAuthVersion() == null ? 0 : user.getAuthVersion()) + 1);
        userMapper.updateById(user);
        int revoked = revokeAllWebSessions(userId);
        auditService.record(userId, "PASSWORD_CHANGE", "USER", user.getPublicId());
        return new PasswordChangeResponse(revoked);
    }

    public UserView currentUser() {
        UserEntity user = userMapper.selectById(CurrentUser.require().userId());
        if (user == null) {
            throw new UnauthorizedException("User is unavailable");
        }
        return UserView.from(user);
    }

    public ClientContext clientContext(String userAgent, String remoteAddress) {
        String normalizedAgent = userAgent == null ? "" : userAgent.trim();
        String lower = normalizedAgent.toLowerCase(Locale.ROOT);
        String clientType = lower.contains("powershell") || lower.contains("curl")
                || lower.contains("postman") || lower.contains("httpie") ? "CLI" : "WEB";
        String label;
        if (lower.contains("edg/")) label = "Microsoft Edge";
        else if (lower.contains("chrome/")) label = "Google Chrome";
        else if (lower.contains("firefox/")) label = "Mozilla Firefox";
        else if ("CLI".equals(clientType)) label = "API client";
        else label = "Web session";
        String agentHash = normalizedAgent.isBlank() ? null : jwtService.digest(normalizedAgent);
        return new ClientContext(clientType, label, agentHash, maskIp(remoteAddress));
    }

    private AuthResponse issueTokenPair(UserEntity user, String familyId, ClientContext clientContext) {
        ClientContext context = clientContext == null ? ClientContext.unknown() : clientContext;
        String accessToken = jwtService.createAccessToken(user, familyId);
        String refreshToken = jwtService.createRefreshToken(user, familyId);
        String digest = jwtService.digest(refreshToken);
        RefreshTokenEntity record = new RefreshTokenEntity();
        record.setUserId(user.getId());
        record.setTokenHash(digest);
        record.setFamilyId(familyId);
        record.setClientType(context.clientType());
        record.setClientLabel(context.clientLabel());
        record.setUserAgentHash(context.userAgentHash());
        record.setIpMasked(context.ipMasked());
        record.setLastUsedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.refreshExpirationSeconds()));
        refreshTokenMapper.insert(record);
        redisTemplate.opsForValue().set(
                REDIS_PREFIX + digest,
                String.valueOf(user.getId()),
                jwtService.refreshExpirationSeconds(),
                TimeUnit.SECONDS);
        return new AuthResponse(accessToken, refreshToken, jwtService.accessExpirationSeconds(), UserView.from(user));
    }

    private int revokeAllWebSessions(Long userId) {
        List<RefreshTokenEntity> active = refreshTokenMapper.selectList(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .gt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now()));
        active.stream().map(RefreshTokenEntity::getFamilyId).filter(Objects::nonNull).distinct()
                .forEach(familyId -> revokeFamily(userId, familyId));
        return active.size();
    }

    private int revokeFamily(Long userId, String familyId) {
        if (familyId == null || familyId.isBlank()) return 0;
        List<RefreshTokenEntity> family = refreshTokenMapper.selectList(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getFamilyId, familyId)
                .isNull(RefreshTokenEntity::getRevokedAt));
        LocalDateTime now = LocalDateTime.now();
        for (RefreshTokenEntity token : family) {
            token.setRevokedAt(now);
            refreshTokenMapper.updateById(token);
            redisTemplate.delete(REDIS_PREFIX + token.getTokenHash());
        }
        redisTemplate.opsForValue().set(
                FAMILY_REVOKED_PREFIX + familyId,
                "1",
                jwtService.refreshExpirationSeconds(),
                TimeUnit.SECONDS);
        return family.size();
    }

    private String maskIp(String value) {
        if (value == null || value.isBlank()) return null;
        String ip = value.trim();
        if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            int lastDot = ip.lastIndexOf('.');
            return ip.substring(0, lastDot + 1) + "0";
        }
        if (ip.contains(":")) {
            String[] segments = ip.split(":");
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < Math.min(4, segments.length); i++) {
                if (i > 0) masked.append(':');
                masked.append(segments[i]);
            }
            return masked.append("::").toString();
        }
        return "masked";
    }

    public record ClientContext(String clientType, String clientLabel, String userAgentHash, String ipMasked) {
        public ClientContext {
            clientType = clientType == null || clientType.isBlank() ? "WEB" : clientType;
            clientLabel = clientLabel == null || clientLabel.isBlank() ? "Web session" : clientLabel;
        }

        public static ClientContext unknown() {
            return new ClientContext("WEB", "Web session", null, null);
        }

        boolean isUnknown() {
            return userAgentHash == null && ipMasked == null;
        }
    }

    private void ensureLoginAllowed(String failureKey) {
        String value = redisTemplate.opsForValue().get(failureKey);
        if (value != null) {
            try {
                if (Long.parseLong(value) >= MAX_LOGIN_FAILURES) {
                    throw new BusinessException(4291001, "Too many login attempts; try again later", HttpStatus.TOO_MANY_REQUESTS);
                }
            } catch (NumberFormatException ignored) {
                redisTemplate.delete(failureKey);
            }
        }
    }

    private void recordLoginFailure(String failureKey) {
        Long attempts = redisTemplate.opsForValue().increment(failureKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(failureKey, 15, TimeUnit.MINUTES);
        }
    }
}

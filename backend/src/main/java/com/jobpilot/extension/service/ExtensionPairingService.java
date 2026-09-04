package com.jobpilot.extension.service;

import static com.jobpilot.extension.dto.ExtensionDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.auth.service.JwtService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.extension.config.ExtensionProperties;
import com.jobpilot.extension.domain.ExtensionDeviceEntity;
import com.jobpilot.extension.domain.ExtensionPairingCodeEntity;
import com.jobpilot.extension.domain.ExtensionRefreshTokenEntity;
import com.jobpilot.extension.mapper.ExtensionDeviceMapper;
import com.jobpilot.extension.mapper.ExtensionPairingCodeMapper;
import com.jobpilot.extension.mapper.ExtensionRefreshTokenMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtensionPairingService {
    public static final List<String> SCOPES = List.of(
            "JOB_CAPTURE", "MATCH_READ", "QUEUE_WRITE", "DRAFT_WRITE", "ASSIST_PREPARE");
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final ExtensionPairingCodeMapper pairingCodes;
    private final ExtensionDeviceMapper devices;
    private final ExtensionRefreshTokenMapper refreshTokens;
    private final UserMapper users;
    private final JwtService jwt;
    private final JsonCodec json;
    private final ExtensionProperties properties;
    private final AuditService audit;

    public ExtensionPairingService(ExtensionPairingCodeMapper pairingCodes, ExtensionDeviceMapper devices,
                                   ExtensionRefreshTokenMapper refreshTokens, UserMapper users,
                                   JwtService jwt, JsonCodec json, ExtensionProperties properties,
                                   AuditService audit) {
        this.pairingCodes = pairingCodes; this.devices = devices; this.refreshTokens = refreshTokens;
        this.users = users; this.jwt = jwt; this.json = json; this.properties = properties; this.audit = audit;
    }

    @Transactional
    public PairingCodeView createCode(Long userId) {
        String code = randomCode();
        ExtensionPairingCodeEntity entity = new ExtensionPairingCodeEntity();
        entity.setUserId(userId); entity.setCodeHash(hash(normalizeCode(code))); entity.setScopesJson(json.write(SCOPES));
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(properties.getPairingExpirationSeconds()));
        entity.setVersion(0); pairingCodes.insert(entity);
        audit.record(userId, "EXTENSION_PAIRING_CODE_CREATE", "EXTENSION_PAIRING_CODE", entity.getPublicId());
        return new PairingCodeView(code, entity.getExpiresAt(), SCOPES);
    }

    @Transactional
    public ExtensionTokensView pair(PairingRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ExtensionPairingCodeEntity code = pairingCodes.selectOne(new LambdaQueryWrapper<ExtensionPairingCodeEntity>()
                .eq(ExtensionPairingCodeEntity::getCodeHash, hash(normalizeCode(request.pairingCode()))).last("LIMIT 1"));
        if (code == null || code.getUsedAt() != null || !code.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Pairing code is invalid or expired");
        }
        UserEntity user = activeUser(code.getUserId());
        ExtensionDeviceEntity device = new ExtensionDeviceEntity();
        device.setUserId(user.getId()); device.setDeviceName(request.deviceName().trim());
        device.setBrowserName(request.browserName().trim()); device.setExtensionId(request.extensionId().trim());
        device.setExtensionVersion(request.extensionVersion().trim()); device.setScopesJson(json.write(SCOPES));
        device.setStatus("ACTIVE"); device.setTokenVersion(1); device.setPairedAt(now); device.setLastSeenAt(now);
        devices.insert(device);
        if (pairingCodes.consume(code.getId(), device.getId(), now) != 1) {
            throw new BusinessException(4097101, "Pairing code was already consumed", HttpStatus.CONFLICT);
        }
        String refresh = newRefreshToken();
        insertRefresh(user.getId(), device.getId(), refresh, now);
        audit.record(user.getId(), "EXTENSION_DEVICE_PAIR", "EXTENSION_DEVICE", device.getPublicId());
        return tokens(user, device, refresh);
    }

    @Transactional
    public ExtensionTokensView refresh(RefreshRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ExtensionRefreshTokenEntity current = refreshTokens.selectOne(new LambdaQueryWrapper<ExtensionRefreshTokenEntity>()
                .eq(ExtensionRefreshTokenEntity::getTokenHash, hash(request.refreshToken())).last("LIMIT 1"));
        if (current == null || current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Extension refresh token is invalid or expired");
        }
        ExtensionDeviceEntity device = devices.selectById(current.getDeviceId());
        if (device == null || !"ACTIVE".equals(device.getStatus())) throw new UnauthorizedException("Extension device is revoked");
        UserEntity user = activeUser(current.getUserId());
        String replacementValue = newRefreshToken();
        ExtensionRefreshTokenEntity replacement = insertRefresh(user.getId(), device.getId(), replacementValue, now);
        if (refreshTokens.rotate(current.getId(), replacement.getId(), now) != 1) {
            throw new UnauthorizedException("Extension refresh token was already rotated");
        }
        device.setLastSeenAt(now); devices.updateById(device);
        audit.record(user.getId(), "EXTENSION_TOKEN_REFRESH", "EXTENSION_DEVICE", device.getPublicId());
        return tokens(user, device, replacementValue);
    }

    public List<ExtensionDeviceView> list(Long userId) {
        return devices.selectList(new LambdaQueryWrapper<ExtensionDeviceEntity>()
                .eq(ExtensionDeviceEntity::getUserId, userId).orderByDesc(ExtensionDeviceEntity::getCreatedAt))
                .stream().map(this::view).toList();
    }

    @Transactional
    public void revoke(Long userId, String publicId) {
        ExtensionDeviceEntity device = devices.selectOne(new LambdaQueryWrapper<ExtensionDeviceEntity>()
                .eq(ExtensionDeviceEntity::getUserId, userId).eq(ExtensionDeviceEntity::getPublicId, publicId).last("LIMIT 1"));
        if (device == null) throw new ResourceNotFoundException("Extension device");
        if ("REVOKED".equals(device.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        device.setStatus("REVOKED"); device.setRevokedAt(now); device.setTokenVersion(device.getTokenVersion() + 1);
        devices.updateById(device); refreshTokens.revokeDevice(device.getId(), now);
        audit.record(userId, "EXTENSION_DEVICE_REVOKE", "EXTENSION_DEVICE", publicId);
    }

    public ExtensionDeviceEntity requireActive(Long userId, String publicId) {
        ExtensionDeviceEntity device = devices.selectOne(new LambdaQueryWrapper<ExtensionDeviceEntity>()
                .eq(ExtensionDeviceEntity::getUserId, userId).eq(ExtensionDeviceEntity::getPublicId, publicId).last("LIMIT 1"));
        if (device == null || !"ACTIVE".equals(device.getStatus())) throw new UnauthorizedException("Extension device is revoked");
        return device;
    }

    private ExtensionRefreshTokenEntity insertRefresh(Long userId, Long deviceId, String token, LocalDateTime now) {
        ExtensionRefreshTokenEntity entity = new ExtensionRefreshTokenEntity();
        entity.setUserId(userId); entity.setDeviceId(deviceId); entity.setTokenHash(hash(token));
        entity.setExpiresAt(now.plusSeconds(properties.getRefreshExpirationSeconds())); refreshTokens.insert(entity);
        return entity;
    }

    private ExtensionTokensView tokens(UserEntity user, ExtensionDeviceEntity device, String refresh) {
        String access = "jpe_" + jwt.createExtensionAccessToken(user, device.getPublicId(), device.getTokenVersion(),
                SCOPES, properties.getAccessExpirationSeconds());
        return new ExtensionTokensView(access, refresh, properties.getAccessExpirationSeconds(), view(device), SCOPES);
    }

    private ExtensionDeviceView view(ExtensionDeviceEntity item) {
        return new ExtensionDeviceView(item.getPublicId(), item.getDeviceName(), item.getBrowserName(),
                item.getExtensionId(), item.getExtensionVersion(), json.readStringList(item.getScopesJson()),
                item.getStatus(), item.getPairedAt(), item.getLastSeenAt(), item.getRevokedAt());
    }

    private UserEntity activeUser(Long id) {
        UserEntity user = users.selectById(id);
        if (user == null || !"ACTIVE".equals(user.getStatus())) throw new UnauthorizedException("User is unavailable");
        return user;
    }

    private String randomCode() {
        StringBuilder value = new StringBuilder("JP-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) value.append('-');
            value.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return value.toString();
    }

    private String newRefreshToken() {
        byte[] value = new byte[32]; random.nextBytes(value);
        return "jpr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

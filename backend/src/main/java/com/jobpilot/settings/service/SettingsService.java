package com.jobpilot.settings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.RefreshTokenEntity;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.RefreshTokenMapper;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.UnauthorizedException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.settings.domain.SystemSettingEntity;
import com.jobpilot.settings.dto.SettingsDtos.AccountUpdateRequest;
import com.jobpilot.settings.dto.SettingsDtos.AccountView;
import com.jobpilot.settings.dto.SettingsDtos.SecuritySummary;
import com.jobpilot.settings.dto.SettingsDtos.SettingUpdateRequest;
import com.jobpilot.settings.dto.SettingsDtos.SettingView;
import com.jobpilot.settings.dto.SettingsDtos.SettingsOverview;
import com.jobpilot.settings.mapper.SystemSettingMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final Set<String> LOCALES = Set.of("zh-CN", "en-US");
    private static final Set<String> LANDING_PAGES = Set.of(
            "/dashboard", "/setup", "/jobs", "/recommendations", "/applications");
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private final SystemSettingMapper settings;
    private final UserMapper users;
    private final RefreshTokenMapper refreshTokens;
    private final JsonCodec json;
    private final AuditService audit;

    public SettingsService(SystemSettingMapper settings, UserMapper users, RefreshTokenMapper refreshTokens,
                           JsonCodec json, AuditService audit) {
        this.settings = settings;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.json = json;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SettingsOverview overview(Long userId) {
        UserEntity user = requireUser(userId);
        Map<String, SystemSettingEntity> persisted = new LinkedHashMap<>();
        settings.selectList(new LambdaQueryWrapper<SystemSettingEntity>()
                        .eq(SystemSettingEntity::getUserId, userId))
                .forEach(value -> persisted.put(identifier(value.getSettingGroup(), value.getSettingKey()), value));
        List<SettingView> preferences = DEFINITIONS.entrySet().stream()
                .map(entry -> view(entry.getValue(), persisted.get(entry.getKey())))
                .toList();
        long activeSessions = refreshTokens.selectCount(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .gt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now()));
        return new SettingsOverview(accountView(user), preferences,
                new SecuritySummary(activeSessions, true, user.getPasswordChangedAt()));
    }

    @Transactional
    public AccountView updateAccount(Long userId, AccountUpdateRequest request) {
        UserEntity user = requireUser(userId);
        if (!request.version().equals(user.getVersion())) {
            throw conflict("Account settings changed in another request; refresh and try again");
        }
        String timezone = request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (ZoneRulesException exception) {
            throw new ValidationException("Timezone is not a valid IANA timezone");
        }
        if (!LOCALES.contains(request.locale())) {
            throw new ValidationException("Locale must be zh-CN or en-US");
        }
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeNullable(request.email()));
        user.setTimezone(timezone);
        user.setLocale(request.locale());
        try {
            if (users.updateById(user) != 1) {
                throw conflict("Account settings changed in another request; refresh and try again");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("Email is already in use");
        }
        audit.record(userId, "ACCOUNT_UPDATE", "USER", user.getPublicId());
        return accountView(requireUser(userId));
    }

    @Transactional
    public SettingView updateSetting(Long userId, String group, String key, SettingUpdateRequest request) {
        Definition definition = DEFINITIONS.get(identifier(group, key));
        if (definition == null) {
            throw new ValidationException("Setting is not supported");
        }
        Object value = definition.validate(request.value());
        SystemSettingEntity existing = settings.selectOne(new LambdaQueryWrapper<SystemSettingEntity>()
                .eq(SystemSettingEntity::getUserId, userId)
                .eq(SystemSettingEntity::getSettingGroup, definition.group())
                .eq(SystemSettingEntity::getSettingKey, definition.key())
                .last("LIMIT 1"));
        if (existing == null) {
            if (request.version() != null && request.version() != 0) {
                throw conflict("Setting changed in another request; refresh and try again");
            }
            existing = new SystemSettingEntity();
            existing.setUserId(userId);
            existing.setSettingGroup(definition.group());
            existing.setSettingKey(definition.key());
            existing.setValueType(definition.type());
            existing.setIsSensitive(false);
            existing.setValueJson(json.write(value));
            existing.setEffectiveAt(LocalDateTime.now());
            settings.insert(existing);
        } else {
            if (request.version() == null || !request.version().equals(existing.getVersion())) {
                throw conflict("Setting changed in another request; refresh and try again");
            }
            existing.setValueJson(json.write(value));
            existing.setEffectiveAt(LocalDateTime.now());
            if (settings.updateById(existing) != 1) {
                throw conflict("Setting changed in another request; refresh and try again");
            }
        }
        audit.record(userId, "SETTING_UPDATE", "SYSTEM_SETTING", definition.group() + "." + definition.key());
        SystemSettingEntity saved = settings.selectOne(new LambdaQueryWrapper<SystemSettingEntity>()
                .eq(SystemSettingEntity::getUserId, userId)
                .eq(SystemSettingEntity::getSettingGroup, definition.group())
                .eq(SystemSettingEntity::getSettingKey, definition.key())
                .last("LIMIT 1"));
        return view(definition, saved);
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = users.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User is unavailable");
        }
        return user;
    }

    private AccountView accountView(UserEntity user) {
        return new AccountView(user.getPublicId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getTimezone(), user.getLocale(), user.getStatus(), user.getLastLoginAt(),
                user.getPasswordChangedAt(), user.getVersion());
    }

    private SettingView view(Definition definition, SystemSettingEntity entity) {
        if (entity == null) {
            return new SettingView(definition.group(), definition.key(), definition.defaultValue(),
                    definition.type(), "DEFAULT", null, null);
        }
        Object value = "BOOLEAN".equals(definition.type())
                ? json.readNode(entity.getValueJson()).asBoolean()
                : json.readNode(entity.getValueJson()).asText();
        return new SettingView(definition.group(), definition.key(), value, definition.type(),
                "STORED", entity.getVersion(), entity.getUpdatedAt());
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        add(result, new Definition("workspace", "defaultLandingPage", "STRING", "/dashboard"));
        add(result, new Definition("workspace", "compactMode", "BOOLEAN", false));
        add(result, new Definition("onboarding", "showDashboardBanner", "BOOLEAN", true));
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Definition> target, Definition definition) {
        target.put(identifier(definition.group(), definition.key()), definition);
    }

    private static String identifier(String group, String key) {
        return (group == null ? "" : group.trim().toLowerCase(Locale.ROOT)) + "."
                + (key == null ? "" : key.trim());
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(4091401, message, HttpStatus.CONFLICT);
    }

    private record Definition(String group, String key, String type, Object defaultValue) {
        Object validate(Object value) {
            if ("BOOLEAN".equals(type)) {
                if (!(value instanceof Boolean)) throw new ValidationException("Setting value must be boolean");
                return value;
            }
            if (!(value instanceof String text)) throw new ValidationException("Setting value must be a string");
            String normalized = text.trim();
            if ("defaultLandingPage".equals(key) && !LANDING_PAGES.contains(normalized)) {
                throw new ValidationException("Default landing page is not supported");
            }
            return normalized;
        }
    }
}

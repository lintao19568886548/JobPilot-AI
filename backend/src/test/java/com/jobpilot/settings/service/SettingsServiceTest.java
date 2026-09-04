package com.jobpilot.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.RefreshTokenMapper;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.settings.domain.SystemSettingEntity;
import com.jobpilot.settings.dto.SettingsDtos.AccountUpdateRequest;
import com.jobpilot.settings.dto.SettingsDtos.SettingUpdateRequest;
import com.jobpilot.settings.mapper.SystemSettingMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {

    private SystemSettingMapper settings;
    private UserMapper users;
    private RefreshTokenMapper refreshTokens;
    private AuditService audit;
    private SettingsService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingMapper.class);
        users = mock(UserMapper.class);
        refreshTokens = mock(RefreshTokenMapper.class);
        audit = mock(AuditService.class);
        service = new SettingsService(settings, users, refreshTokens, new JsonCodec(new ObjectMapper()), audit);
        user = new UserEntity();
        user.setId(9L);
        user.setPublicId("01PHASE14SETTINGS00000000");
        user.setUsername("settings-user");
        user.setDisplayName("Settings User");
        user.setStatus("ACTIVE");
        user.setTimezone("Asia/Shanghai");
        user.setLocale("zh-CN");
        user.setVersion(3);
        when(users.selectById(9L)).thenReturn(user);
    }

    @Test
    void overviewReturnsEveryDocumentedDefault() {
        when(settings.selectList(any())).thenReturn(List.of());
        when(refreshTokens.selectCount(any())).thenReturn(2L);
        var overview = service.overview(9L);
        assertThat(overview.preferences()).hasSize(3);
        assertThat(overview.preferences()).extracting(value -> value.source()).containsOnly("DEFAULT");
        assertThat(overview.security().activeWebSessions()).isEqualTo(2);
        assertThat(overview.security().extensionPairingsManagedSeparately()).isTrue();
    }

    @Test
    void accountUpdateValidatesOptimisticVersion() {
        assertThatThrownBy(() -> service.updateAccount(9L,
                new AccountUpdateRequest("New Name", null, "UTC", "zh-CN", 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("another request");
    }

    @Test
    void accountUpdateRejectsUnknownTimezone() {
        assertThatThrownBy(() -> service.updateAccount(9L,
                new AccountUpdateRequest("New Name", null, "Mars/Olympus", "zh-CN", 3)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("IANA");
    }

    @Test
    void booleanPreferenceIsPersistedAndAudited() {
        AtomicReference<SystemSettingEntity> saved = new AtomicReference<>();
        when(settings.selectOne(any())).thenReturn(null).thenAnswer(invocation -> saved.get());
        doAnswer(invocation -> {
            SystemSettingEntity entity = invocation.getArgument(0);
            entity.setId(41L);
            entity.setPublicId("setting-41");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setVersion(0);
            saved.set(entity);
            return 1;
        }).when(settings).insert(any(SystemSettingEntity.class));

        var result = service.updateSetting(9L, "workspace", "compactMode",
                new SettingUpdateRequest(true, 0));

        assertThat(result.value()).isEqualTo(true);
        assertThat(result.source()).isEqualTo("STORED");
        verify(audit).record(9L, "SETTING_UPDATE", "SYSTEM_SETTING", "workspace.compactMode");
    }

    @Test
    void unsupportedPreferenceIsRejected() {
        assertThatThrownBy(() -> service.updateSetting(9L, "workspace", "secretValue",
                new SettingUpdateRequest("unsafe", 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void landingPageValueOutsideWhitelistIsRejected() {
        assertThatThrownBy(() -> service.updateSetting(9L, "workspace", "defaultLandingPage",
                new SettingUpdateRequest("https://external.invalid", 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void booleanPreferenceRejectsStringCoercion() {
        assertThatThrownBy(() -> service.updateSetting(9L, "workspace", "compactMode",
                new SettingUpdateRequest("true", 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("boolean");
    }
}

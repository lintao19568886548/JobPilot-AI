package com.jobpilot.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.auth.domain.UserEntity;
import com.jobpilot.auth.mapper.UserMapper;
import com.jobpilot.common.config.BootstrapProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUserInitializer.class);
    private final BootstrapProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public BootstrapUserInitializer(
            BootstrapProperties properties,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        String username = normalized(properties.getUsername());
        if (username.isBlank() || properties.getPassword() == null || properties.getPassword().length() < 10) {
            throw new IllegalStateException("Bootstrap user requires username and a password of at least 10 characters");
        }
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        if (existing > 0) {
            log.info("Bootstrap user already exists; no credentials were changed");
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(normalizedNullable(properties.getEmail()));
        user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        user.setDisplayName(properties.getDisplayName());
        user.setStatus("ACTIVE");
        user.setTimezone("Asia/Shanghai");
        user.setLocale("zh-CN");
        user.setAuthVersion(0);
        userMapper.insert(user);
        log.info("Bootstrap user created: userId={}", user.getPublicId());
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizedNullable(String value) {
        String normalized = normalized(value);
        return normalized.isBlank() ? null : normalized;
    }
}

ALTER TABLE users
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER status,
    ADD COLUMN locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN' AFTER timezone,
    ADD COLUMN password_changed_at DATETIME(3) NULL AFTER last_login_at,
    ADD COLUMN auth_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER password_changed_at,
    ADD CONSTRAINT chk_users_locale CHECK (locale IN ('zh-CN', 'en-US'));

ALTER TABLE refresh_tokens
    ADD COLUMN client_type VARCHAR(24) NOT NULL DEFAULT 'WEB' AFTER family_id,
    ADD COLUMN client_label VARCHAR(80) NOT NULL DEFAULT 'Web session' AFTER client_type,
    ADD COLUMN user_agent_hash VARCHAR(64) NULL AFTER client_label,
    ADD COLUMN ip_masked VARCHAR(64) NULL AFTER user_agent_hash,
    ADD COLUMN last_used_at DATETIME(3) NULL AFTER ip_masked,
    ADD CONSTRAINT chk_refresh_tokens_client_type CHECK (client_type IN ('WEB', 'CLI'));

CREATE TABLE system_settings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    setting_group VARCHAR(40) NOT NULL,
    setting_key VARCHAR(64) NOT NULL,
    value_json JSON NULL,
    value_type VARCHAR(16) NOT NULL,
    is_sensitive TINYINT(1) NOT NULL DEFAULT 0,
    encrypted_value VARBINARY(2048) NULL,
    effective_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_settings_public_id (public_id),
    UNIQUE KEY uk_system_settings_user_group_key (user_id, setting_group, setting_key),
    KEY idx_system_settings_user_group (user_id, setting_group),
    CONSTRAINT fk_system_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_system_settings_group CHECK (setting_group IN ('workspace', 'onboarding')),
    CONSTRAINT chk_system_settings_type CHECK (value_type IN ('STRING', 'BOOLEAN')),
    CONSTRAINT chk_system_settings_sensitive_storage CHECK (
        (is_sensitive = 0 AND value_json IS NOT NULL AND encrypted_value IS NULL)
        OR (is_sensitive = 1 AND value_json IS NULL AND encrypted_value IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

package com.jobpilot.extension.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("extension_refresh_tokens")
public class ExtensionRefreshTokenEntity extends AuditEntity {
    private Long userId;
    private Long deviceId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long replacedById;
}

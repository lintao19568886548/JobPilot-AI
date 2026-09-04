package com.jobpilot.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("refresh_tokens")
public class RefreshTokenEntity extends BaseEntity {

    private Long userId;
    private String tokenHash;
    private String familyId;
    private String clientType;
    private String clientLabel;
    private String userAgentHash;
    private String ipMasked;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String replacedByHash;
}

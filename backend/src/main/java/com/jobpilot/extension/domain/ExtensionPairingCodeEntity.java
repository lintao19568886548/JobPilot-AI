package com.jobpilot.extension.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.jobpilot.common.persistence.AuditEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("extension_pairing_codes")
public class ExtensionPairingCodeEntity extends AuditEntity {
    private Long userId;
    private String codeHash;
    private String scopesJson;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Long usedByDeviceId;
    @Version
    private Integer version;
}

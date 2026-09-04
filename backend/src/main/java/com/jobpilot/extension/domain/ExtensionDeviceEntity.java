package com.jobpilot.extension.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("extension_devices")
public class ExtensionDeviceEntity extends BaseEntity {
    private Long userId;
    private String deviceName;
    private String browserName;
    private String extensionId;
    private String extensionVersion;
    private String scopesJson;
    private String status;
    private Integer tokenVersion;
    private LocalDateTime pairedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;
}

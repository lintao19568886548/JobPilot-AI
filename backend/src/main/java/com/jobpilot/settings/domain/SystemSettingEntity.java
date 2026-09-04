package com.jobpilot.settings.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("system_settings")
public class SystemSettingEntity extends BaseEntity {

    private Long userId;
    private String settingGroup;
    private String settingKey;
    private String valueJson;
    private String valueType;
    @TableField(value = "is_sensitive")
    private Boolean isSensitive;
    private byte[] encryptedValue;
    private LocalDateTime effectiveAt;
}

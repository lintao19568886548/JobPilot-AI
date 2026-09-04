package com.jobpilot.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("users")
public class UserEntity extends BaseEntity {

    private String username;
    private String email;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private String status;
    private String timezone;
    private String locale;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private Integer authVersion;
}

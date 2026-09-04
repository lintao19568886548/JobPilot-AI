package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("match_configs")
public class MatchConfigEntity extends BaseEntity {
    private Long userId;
    private String name;
    private Integer versionNo;
    private String weightsJson;
    private String levelThresholdsJson;
    private String penaltiesJson;
    private String algorithmVersion;
    private Boolean active;
    private LocalDateTime effectiveAt;
}

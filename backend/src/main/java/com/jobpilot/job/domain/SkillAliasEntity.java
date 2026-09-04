package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("skill_aliases")
public class SkillAliasEntity extends BaseEntity {
    private Long skillId;
    private String aliasName;
    private String normalizedAlias;
    private String source;
    private BigDecimal confidence;
    private Boolean active;
}

package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("skill_relations")
public class SkillRelationEntity extends BaseEntity {
    private Long fromSkillId;
    private Long toSkillId;
    private String relationType;
    private BigDecimal weight;
    private String source;
}

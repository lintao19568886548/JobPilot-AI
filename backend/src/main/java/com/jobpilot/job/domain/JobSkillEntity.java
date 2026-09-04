package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_skills")
public class JobSkillEntity extends BaseEntity {
    private Long jobId;
    private Long skillId;
    private String requirementType;
    private Integer importance;
    private BigDecimal minYears;
    private String evidenceText;
    private String source;
    private BigDecimal confidence;
}

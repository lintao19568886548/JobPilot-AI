package com.jobpilot.candidate.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("candidate_skills")
public class CandidateSkillEntity extends BaseEntity {

    private Long userId;
    private Long candidateProfileId;
    private Long skillId;
    private Integer proficiency;
    private BigDecimal years;
    private LocalDate lastUsedAt;
    private String source;
    private Boolean primarySkill;
}


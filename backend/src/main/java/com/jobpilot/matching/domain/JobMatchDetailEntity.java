package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_match_details")
public class JobMatchDetailEntity extends AuditEntity {
    private Long jobMatchId;
    private String dimensionName;
    private String itemKey;
    private String candidateEvidenceRef;
    private String jobEvidenceText;
    private BigDecimal score;
    private String decision;
    private String explanation;
}

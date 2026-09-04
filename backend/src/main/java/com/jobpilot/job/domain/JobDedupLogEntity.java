package com.jobpilot.job.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_dedup_logs")
public class JobDedupLogEntity extends AuditEntity {
    private Long userId;
    private Long incomingSourceId;
    private Long candidateJobId;
    private BigDecimal ruleScore;
    private BigDecimal embeddingScore;
    private String decision;
    private String algorithmVersion;
    private String decidedBy;
    private String detailJson;
}

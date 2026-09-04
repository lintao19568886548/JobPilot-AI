package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("job_matches")
public class JobMatchEntity extends BaseEntity {
    private Long userId;
    private Long jobId;
    private Long candidateProfileId;
    private Long resumeVersionId;
    private Long matchConfigId;
    private Long matchRunId;
    private Long aiCallId;
    private String inputHash;
    private String hardFilterResult;
    private String hardFilterJson;
    private BigDecimal skillScore;
    private BigDecimal embeddingScore;
    private BigDecimal llmScore;
    private BigDecimal projectScore;
    private BigDecimal preferenceScore;
    private BigDecimal companyScore;
    private BigDecimal penaltyScore;
    private BigDecimal overallScore;
    @TableField("level_code")
    private String level;
    private String advantagesJson;
    private String gapsJson;
    private String risksJson;
    private String recommendation;
    private String reasonText;
    private Long recommendedResumeVersionId;
    private String algorithmVersion;
    private Integer configVersion;
    private String weightsSnapshotJson;
    private String thresholdsSnapshotJson;
    private String effectiveWeightsJson;
    private String embeddingProvider;
    private String embeddingModel;
    private String embeddingVersion;
    private String llmStatus;
    private String promptVersion;
    private String modelName;
    private String status;
    private LocalDateTime evaluatedAt;
}

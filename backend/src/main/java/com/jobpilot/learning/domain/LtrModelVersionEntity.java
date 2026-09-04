package com.jobpilot.learning.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("ltr_model_versions")
public class LtrModelVersionEntity extends BaseEntity {
    private Long userId;
    private Long trainingRunId;
    private Integer versionNo;
    private String name;
    private String modelType;
    private String featureSchemaJson;
    private String parametersJson;
    private String trainingWindowJson;
    private Integer sampleCount;
    private String evaluationJson;
    private String status;
    private Boolean activationEligible;
    private LocalDateTime shadowedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime retiredAt;
}

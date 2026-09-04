package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("candidate_evidence_items")
public class CandidateEvidenceItemEntity extends AuditEntity {
    private Long userId;
    private String evidenceKey;
    private String evidenceType;
    private String sourceType;
    private String sourcePublicId;
    private String fieldPath;
    private String valueJson;
    private String normalizedText;
    private String contentHash;
    private Integer versionNo;
}

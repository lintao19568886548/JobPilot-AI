package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.AuditEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("resume_tailor_changes")
public class ResumeTailorChangeEntity extends AuditEntity {
    private Long tailorRunId;
    private String sectionType;
    private String operation;
    private String beforeText;
    private String afterText;
    private String reasonText;
    private String evidenceRefsJson;
    private Integer sortOrder;
}

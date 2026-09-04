package com.jobpilot.tailoring.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("prompt_templates")
public class PromptTemplateEntity extends BaseEntity {
    private String templateKey;
    private Integer versionNo;
    private String displayName;
    private String systemPrompt;
    private String inputSchemaVersion;
    private String outputSchemaVersion;
    private String promptHash;
    private Boolean active;
    private String createdBy;
}

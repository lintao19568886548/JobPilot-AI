package com.jobpilot.matching.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jobpilot.common.persistence.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("entity_embeddings")
public class EntityEmbeddingEntity extends BaseEntity {
    private Long userId;
    private String entityType;
    private Long entityId;
    private String entityPublicId;
    private String vectorStore;
    private String collectionName;
    private String vectorId;
    private String embeddingProvider;
    private String embeddingModel;
    private Integer dimension;
    private String contentHash;
    private String embeddingVersion;
    private String status;
    private LocalDateTime embeddedAt;
}

CREATE TABLE match_configs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    weights_json JSON NOT NULL,
    level_thresholds_json JSON NOT NULL,
    penalties_json JSON NOT NULL,
    algorithm_version VARCHAR(80) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 0,
    effective_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN active = 1 AND deleted_at IS NULL THEN user_id ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_match_configs_public_id (public_id),
    UNIQUE KEY uk_match_configs_user_version (user_id, version_no),
    UNIQUE KEY uk_match_configs_active_user (active_user_id),
    KEY idx_match_configs_user_time (user_id, created_at DESC),
    CONSTRAINT fk_match_configs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_match_configs_active CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE hard_filter_rules (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    rule_key VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    rule_operator VARCHAR(24) NOT NULL,
    operand_json JSON NOT NULL,
    result_action VARCHAR(24) NOT NULL,
    penalty DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    priority INT NOT NULL DEFAULT 100,
    active TINYINT(1) NOT NULL DEFAULT 1,
    version_no INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_rule_key VARCHAR(170) GENERATED ALWAYS AS
        (CASE WHEN active = 1 AND deleted_at IS NULL THEN CONCAT(user_id, ':', rule_key) ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hard_filter_rules_public_id (public_id),
    UNIQUE KEY uk_hard_filter_rules_version (user_id, rule_key, version_no),
    UNIQUE KEY uk_hard_filter_rules_active (active_rule_key),
    KEY idx_hard_filter_rules_eval (user_id, active, priority, id),
    CONSTRAINT fk_hard_filter_rules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_hard_filter_rules_type CHECK (rule_type IN
        ('GRADUATION_YEAR','EDUCATION','CITY','EXPERIENCE_YEARS','JOB_TYPE','SALARY','COMPANY_BLACKLIST','JOB_KEYWORD_BLACKLIST')),
    CONSTRAINT chk_hard_filter_rules_operator CHECK (rule_operator IN
        ('EQ','NE','GTE','LTE','IN','NOT_IN','CONTAINS','RANGE')),
    CONSTRAINT chk_hard_filter_rules_action CHECK (result_action IN ('REJECT','DOWNGRADE','WARN')),
    CONSTRAINT chk_hard_filter_rules_penalty CHECK (penalty BETWEEN 0 AND 100),
    CONSTRAINT chk_hard_filter_rules_active CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_relations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    from_skill_id BIGINT UNSIGNED NOT NULL,
    to_skill_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(24) NOT NULL,
    weight DECIMAL(4,3) NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_relations_public_id (public_id),
    UNIQUE KEY uk_skill_relations_active (from_skill_id, to_skill_id, relation_type, active_marker),
    KEY idx_skill_relations_reverse (to_skill_id, relation_type, deleted_at),
    CONSTRAINT fk_skill_relations_from FOREIGN KEY (from_skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT fk_skill_relations_to FOREIGN KEY (to_skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_relations_type CHECK (relation_type IN ('PARENT','RELATED','ALTERNATIVE','PART_OF')),
    CONSTRAINT chk_skill_relations_weight CHECK (weight BETWEEN 0 AND 1),
    CONSTRAINT chk_skill_relations_distinct CHECK (from_skill_id <> to_skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    candidate_profile_id BIGINT UNSIGNED NOT NULL,
    resume_version_id BIGINT UNSIGNED NULL,
    match_config_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    force_run TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 3,
    result_match_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(80) NULL,
    error_message_safe VARCHAR(500) NULL,
    available_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_match_runs_public_id (public_id),
    UNIQUE KEY uk_match_runs_idempotency (user_id, idempotency_key),
    KEY idx_match_runs_worker (status, available_at, id),
    KEY idx_match_runs_job_time (user_id, job_id, created_at DESC),
    KEY idx_match_runs_input (user_id, input_hash, status),
    CONSTRAINT fk_match_runs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_runs_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_runs_profile FOREIGN KEY (candidate_profile_id) REFERENCES candidate_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_runs_resume_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_runs_config FOREIGN KEY (match_config_id) REFERENCES match_configs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_match_runs_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','DEAD')),
    CONSTRAINT chk_match_runs_force CHECK (force_run IN (0, 1)),
    CONSTRAINT chk_match_runs_attempts CHECK (attempt_count <= max_attempts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO skill_relations
    (public_id, from_skill_id, to_skill_id, relation_type, weight, source, created_at, updated_at, version)
SELECT '01M1F000000000000000000001', child.id, parent.id, 'PART_OF', 0.850, 'SYSTEM', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
FROM skills child JOIN skills parent ON parent.canonical_name = 'spring'
WHERE child.canonical_name = 'spring_boot'
UNION ALL
SELECT '01M1F000000000000000000002', child.id, parent.id, 'PART_OF', 0.800, 'SYSTEM', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
FROM skills child JOIN skills parent ON parent.canonical_name = 'spring'
WHERE child.canonical_name = 'spring_cloud'
UNION ALL
SELECT '01M1F000000000000000000003', child.id, parent.id, 'RELATED', 0.650, 'SYSTEM', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
FROM skills child JOIN skills parent ON parent.canonical_name = 'kubernetes'
WHERE child.canonical_name = 'docker'
UNION ALL
SELECT '01M1F000000000000000000004', child.id, parent.id, 'ALTERNATIVE', 0.550, 'SYSTEM', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
FROM skills child JOIN skills parent ON parent.canonical_name = 'kafka'
WHERE child.canonical_name = 'rabbitmq';

CREATE TABLE ai_call_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(160) NULL,
    prompt_version VARCHAR(80) NULL,
    prompt_hash CHAR(64) NULL,
    request_hash CHAR(64) NOT NULL,
    response_hash CHAR(64) NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    estimated_cost DECIMAL(12,6) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    duration_ms INT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(80) NULL,
    trace_id VARCHAR(64) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_call_logs_public_id (public_id),
    KEY idx_ai_call_logs_user_time (user_id, created_at DESC),
    KEY idx_ai_call_logs_task_status (task_type, status, created_at),
    CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_call_logs_status CHECK (status IN ('SUCCEEDED','FAILED','SKIPPED_NOT_CONFIGURED')),
    CONSTRAINT chk_ai_call_logs_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_matches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    candidate_profile_id BIGINT UNSIGNED NOT NULL,
    resume_version_id BIGINT UNSIGNED NULL,
    match_config_id BIGINT UNSIGNED NOT NULL,
    match_run_id BIGINT UNSIGNED NOT NULL,
    ai_call_id BIGINT UNSIGNED NULL,
    input_hash CHAR(64) NOT NULL,
    hard_filter_result VARCHAR(24) NOT NULL,
    hard_filter_json JSON NOT NULL,
    skill_score DECIMAL(5,2) NULL,
    embedding_score DECIMAL(5,2) NULL,
    llm_score DECIMAL(5,2) NULL,
    project_score DECIMAL(5,2) NULL,
    preference_score DECIMAL(5,2) NULL,
    company_score DECIMAL(5,2) NULL,
    penalty_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    overall_score DECIMAL(5,2) NULL,
    level_code CHAR(1) NULL,
    advantages_json JSON NOT NULL,
    gaps_json JSON NOT NULL,
    risks_json JSON NOT NULL,
    recommendation VARCHAR(32) NOT NULL,
    reason_text VARCHAR(1000) NOT NULL,
    recommended_resume_version_id BIGINT UNSIGNED NULL,
    algorithm_version VARCHAR(80) NOT NULL,
    config_version INT UNSIGNED NOT NULL,
    weights_snapshot_json JSON NOT NULL,
    thresholds_snapshot_json JSON NOT NULL,
    effective_weights_json JSON NOT NULL,
    embedding_provider VARCHAR(80) NULL,
    embedding_model VARCHAR(160) NULL,
    embedding_version VARCHAR(80) NULL,
    llm_status VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(80) NULL,
    model_name VARCHAR(160) NULL,
    status VARCHAR(24) NOT NULL,
    evaluated_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_matches_public_id (public_id),
    UNIQUE KEY uk_job_matches_run (match_run_id),
    KEY idx_job_matches_job_history (user_id, job_id, evaluated_at DESC, id DESC),
    KEY idx_job_matches_input (user_id, input_hash, status),
    CONSTRAINT fk_job_matches_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_profile FOREIGN KEY (candidate_profile_id) REFERENCES candidate_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_resume FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_recommended_resume FOREIGN KEY (recommended_resume_version_id) REFERENCES resume_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_config FOREIGN KEY (match_config_id) REFERENCES match_configs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_run FOREIGN KEY (match_run_id) REFERENCES match_runs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_matches_ai_call FOREIGN KEY (ai_call_id) REFERENCES ai_call_logs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_matches_hard_result CHECK (hard_filter_result IN ('PASS','DOWNGRADE','REJECT')),
    CONSTRAINT chk_job_matches_level CHECK (level_code IS NULL OR level_code IN ('S','A','B','C','D')),
    CONSTRAINT chk_job_matches_recommendation CHECK (recommendation IN ('RECOMMEND','CONSIDER','NOT_RECOMMENDED','REJECTED')),
    CONSTRAINT chk_job_matches_llm_status CHECK (llm_status IN ('SUCCEEDED','FAILED','SKIPPED_NOT_CONFIGURED')),
    CONSTRAINT chk_job_matches_status CHECK (status IN ('SUCCEEDED','REJECTED')),
    CONSTRAINT chk_job_matches_scores CHECK (
        (skill_score IS NULL OR skill_score BETWEEN 0 AND 100) AND
        (embedding_score IS NULL OR embedding_score BETWEEN 0 AND 100) AND
        (llm_score IS NULL OR llm_score BETWEEN 0 AND 100) AND
        (project_score IS NULL OR project_score BETWEEN 0 AND 100) AND
        (preference_score IS NULL OR preference_score BETWEEN 0 AND 100) AND
        (company_score IS NULL OR company_score BETWEEN 0 AND 100) AND
        penalty_score BETWEEN 0 AND 100 AND
        (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE match_runs
    ADD CONSTRAINT fk_match_runs_result_match FOREIGN KEY (result_match_id) REFERENCES job_matches (id) ON DELETE RESTRICT;

CREATE TABLE job_match_details (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    job_match_id BIGINT UNSIGNED NOT NULL,
    dimension_name VARCHAR(40) NOT NULL,
    item_key VARCHAR(160) NOT NULL,
    candidate_evidence_ref VARCHAR(200) NULL,
    job_evidence_text VARCHAR(1000) NULL,
    score DECIMAL(5,2) NULL,
    decision VARCHAR(32) NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_match_details_public_id (public_id),
    KEY idx_job_match_details_match_dimension (job_match_id, dimension_name, id),
    CONSTRAINT fk_job_match_details_match FOREIGN KEY (job_match_id) REFERENCES job_matches (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_match_details_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE entity_embeddings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    entity_public_id CHAR(26) NOT NULL,
    vector_store VARCHAR(40) NOT NULL,
    collection_name VARCHAR(160) NOT NULL,
    vector_id VARCHAR(100) NOT NULL,
    embedding_provider VARCHAR(80) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    dimension INT UNSIGNED NOT NULL,
    content_hash CHAR(64) NOT NULL,
    embedding_version VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    embedded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_embeddings_public_id (public_id),
    UNIQUE KEY uk_entity_embeddings_cache (user_id, entity_type, entity_id, embedding_model, content_hash),
    UNIQUE KEY uk_entity_embeddings_vector (collection_name, vector_id),
    KEY idx_entity_embeddings_status (status, updated_at),
    CONSTRAINT fk_entity_embeddings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_entity_embeddings_type CHECK (entity_type IN ('JOB','PROFILE','RESUME_VERSION','PROJECT','SKILL_EVIDENCE')),
    CONSTRAINT chk_entity_embeddings_status CHECK (status IN ('READY','FAILED')),
    CONSTRAINT chk_entity_embeddings_dimension CHECK (dimension > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json JSON NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    available_at DATETIME(3) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 3,
    locked_by VARCHAR(120) NULL,
    locked_at DATETIME(3) NULL,
    published_at DATETIME(3) NULL,
    last_error_safe VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_events_public_id (public_id),
    UNIQUE KEY uk_outbox_events_idempotency (idempotency_key),
    KEY idx_outbox_events_worker (status, available_at, id),
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','DEAD')),
    CONSTRAINT chk_outbox_events_attempts CHECK (attempt_count <= max_attempts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE companies (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    website VARCHAR(500) NULL,
    industry VARCHAR(120) NULL,
    company_size VARCHAR(40) NULL,
    financing_stage VARCHAR(40) NULL,
    headquarters_city VARCHAR(100) NULL,
    description TEXT NULL,
    verified_source VARCHAR(80) NULL,
    risk_flags_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_normalized_name VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN normalized_name ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_public_id (public_id),
    UNIQUE KEY uk_companies_active_name (active_normalized_name),
    KEY idx_companies_industry_name (industry, normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_aliases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    alias_name VARCHAR(120) NOT NULL,
    normalized_alias VARCHAR(120) NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
    confidence DECIMAL(4,3) NOT NULL DEFAULT 1.000,
    active TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_alias VARCHAR(120) GENERATED ALWAYS AS (CASE WHEN active = 1 AND deleted_at IS NULL THEN normalized_alias ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_aliases_public_id (public_id),
    UNIQUE KEY uk_skill_aliases_active_alias (active_alias),
    KEY idx_skill_aliases_skill (skill_id, active, deleted_at),
    CONSTRAINT fk_skill_aliases_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_aliases_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_skill_aliases_active CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE jobs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    company_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    normalized_title VARCHAR(200) NOT NULL,
    canonical_job_key CHAR(64) NOT NULL,
    fingerprint_hash CHAR(64) NOT NULL,
    city VARCHAR(100) NULL,
    district VARCHAR(100) NULL,
    workplace_text VARCHAR(300) NULL,
    remote_type VARCHAR(24) NOT NULL DEFAULT 'ONSITE',
    salary_min DECIMAL(12,2) NULL,
    salary_max DECIMAL(12,2) NULL,
    salary_months TINYINT UNSIGNED NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    salary_text VARCHAR(120) NULL,
    education VARCHAR(40) NULL,
    experience_min_years DECIMAL(4,1) NULL,
    experience_max_years DECIMAL(4,1) NULL,
    graduate_year SMALLINT UNSIGNED NULL,
    job_type VARCHAR(24) NOT NULL DEFAULT 'FULL_TIME',
    description_raw MEDIUMTEXT NOT NULL,
    description_clean MEDIUMTEXT NULL,
    responsibilities_json JSON NOT NULL,
    requirements_json JSON NOT NULL,
    business_domain VARCHAR(160) NULL,
    team_name VARCHAR(160) NULL,
    publish_at DATETIME(3) NULL,
    first_collected_at DATETIME(3) NOT NULL,
    last_collected_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DISCOVERED',
    parse_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    parser_version VARCHAR(40) NULL,
    parser_mode VARCHAR(24) NULL,
    raw_content_hash CHAR(64) NOT NULL,
    parse_result_json JSON NOT NULL,
    manual_fields_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jobs_public_id (public_id),
    KEY idx_jobs_user_status_publish (user_id, deleted_at, status, publish_at DESC, id DESC),
    KEY idx_jobs_user_city_status (user_id, city, status, publish_at DESC),
    KEY idx_jobs_company_title_city (company_id, normalized_title, city),
    KEY idx_jobs_fingerprint (fingerprint_hash),
    KEY idx_jobs_parse_queue (user_id, parse_status, created_at),
    CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT chk_jobs_remote_type CHECK (remote_type IN ('ONSITE','HYBRID','REMOTE','UNKNOWN')),
    CONSTRAINT chk_jobs_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    CONSTRAINT chk_jobs_salary_months CHECK (salary_months IS NULL OR salary_months BETWEEN 1 AND 24),
    CONSTRAINT chk_jobs_experience CHECK (experience_min_years IS NULL OR experience_max_years IS NULL OR experience_max_years >= experience_min_years),
    CONSTRAINT chk_jobs_status CHECK (status IN ('DISCOVERED','PARSED','ACTIVE','EXPIRED','CLOSED','IGNORED')),
    CONSTRAINT chk_jobs_parse_status CHECK (parse_status IN ('PENDING','PROCESSING','SUCCESS','PARTIAL','FAILED')),
    CONSTRAINT chk_jobs_type CHECK (job_type IN ('FULL_TIME','INTERNSHIP','PART_TIME','CONTRACT','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_sources (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    platform VARCHAR(40) NOT NULL,
    platform_job_id VARCHAR(160) NULL,
    source_type VARCHAR(24) NOT NULL,
    job_url VARCHAR(1000) NULL,
    normalized_url_hash CHAR(64) NULL,
    source_title VARCHAR(200) NULL,
    source_company_name VARCHAR(200) NULL,
    raw_snapshot_json JSON NOT NULL,
    publish_at DATETIME(3) NULL,
    collected_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    availability_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    collector_version VARCHAR(80) NULL,
    user_initiated TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_platform_key VARCHAR(240) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL AND platform_job_id IS NOT NULL THEN CONCAT(platform, ':', platform_job_id) ELSE NULL END) STORED,
    active_url_key VARCHAR(110) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL AND normalized_url_hash IS NOT NULL THEN CONCAT(platform, ':', normalized_url_hash) ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_sources_public_id (public_id),
    UNIQUE KEY uk_job_sources_platform_job (active_platform_key),
    UNIQUE KEY uk_job_sources_platform_url (active_url_key),
    KEY idx_job_sources_job_time (job_id, collected_at DESC),
    KEY idx_job_sources_user_platform (user_id, platform, deleted_at),
    CONSTRAINT fk_job_sources_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_sources_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_sources_type CHECK (source_type IN ('MANUAL','URL','EXTENSION','CSV','EXCEL')),
    CONSTRAINT chk_job_sources_availability CHECK (availability_status IN ('AVAILABLE','EXPIRED','CLOSED','UNKNOWN')),
    CONSTRAINT chk_job_sources_user_initiated CHECK (user_initiated IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_skills (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    requirement_type VARCHAR(24) NOT NULL,
    importance TINYINT UNSIGNED NOT NULL DEFAULT 50,
    min_years DECIMAL(4,1) NULL,
    evidence_text VARCHAR(1000) NOT NULL,
    source VARCHAR(40) NOT NULL,
    confidence DECIMAL(4,3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_unique_key VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN CONCAT(job_id, ':', skill_id, ':', requirement_type) ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_skills_public_id (public_id),
    UNIQUE KEY uk_job_skills_active (active_unique_key),
    KEY idx_job_skills_job_type (job_id, requirement_type, importance DESC),
    KEY idx_job_skills_skill (skill_id, requirement_type),
    CONSTRAINT fk_job_skills_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_skills_type CHECK (requirement_type IN ('MUST_HAVE','NICE_TO_HAVE','RELATED')),
    CONSTRAINT chk_job_skills_importance CHECK (importance BETWEEN 0 AND 100),
    CONSTRAINT chk_job_skills_confidence CHECK (confidence BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_import_tasks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    import_type VARCHAR(24) NOT NULL,
    source_url VARCHAR(1000) NULL,
    file_name VARCHAR(255) NULL,
    file_hash CHAR(64) NULL,
    idempotency_key VARCHAR(120) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    failure_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_summary_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_idempotency_key VARCHAR(240) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL AND idempotency_key IS NOT NULL THEN CONCAT(user_id, ':', idempotency_key) ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_import_tasks_public_id (public_id),
    UNIQUE KEY uk_job_import_tasks_idempotency (active_idempotency_key),
    KEY idx_job_import_tasks_user_time (user_id, created_at DESC),
    CONSTRAINT fk_job_import_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_import_tasks_type CHECK (import_type IN ('MANUAL','URL','EXTENSION','CSV','EXCEL')),
    CONSTRAINT chk_job_import_tasks_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','PARTIAL_SUCCESS','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_import_errors (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    import_task_id BIGINT UNSIGNED NOT NULL,
    source_row_number INT UNSIGNED NULL,
    error_code VARCHAR(64) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    field_name VARCHAR(100) NULL,
    raw_preview VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_import_errors_public_id (public_id),
    KEY idx_job_import_errors_task_row (import_task_id, source_row_number),
    CONSTRAINT fk_job_import_errors_task FOREIGN KEY (import_task_id) REFERENCES job_import_tasks (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_parse_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL,
    parser_mode VARCHAR(24) NOT NULL,
    parser_version VARCHAR(40) NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    prompt_version VARCHAR(40) NULL,
    model_name VARCHAR(120) NULL,
    input_hash CHAR(64) NOT NULL,
    output_hash CHAR(64) NULL,
    result_json JSON NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    elapsed_ms INT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_parse_runs_public_id (public_id),
    KEY idx_job_parse_runs_job_time (job_id, created_at DESC),
    KEY idx_job_parse_runs_user_status (user_id, status, created_at),
    CONSTRAINT fk_job_parse_runs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_parse_runs_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_parse_runs_status CHECK (status IN ('PROCESSING','SUCCESS','PARTIAL','FAILED')),
    CONSTRAINT chk_job_parse_runs_mode CHECK (parser_mode IN ('RULES_ONLY','LLM_ONLY','HYBRID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_dedup_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    incoming_source_id BIGINT UNSIGNED NULL,
    candidate_job_id BIGINT UNSIGNED NULL,
    rule_score DECIMAL(5,2) NULL,
    embedding_score DECIMAL(5,4) NULL,
    decision VARCHAR(32) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    decided_by VARCHAR(40) NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_dedup_logs_public_id (public_id),
    KEY idx_job_dedup_logs_user_time (user_id, created_at DESC),
    KEY idx_job_dedup_logs_candidate (candidate_job_id, created_at DESC),
    CONSTRAINT fk_job_dedup_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_dedup_logs_source FOREIGN KEY (incoming_source_id) REFERENCES job_sources (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_dedup_logs_job FOREIGN KEY (candidate_job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_dedup_logs_decision CHECK (decision IN ('NEW_JOB','EXACT_DUPLICATE','RULE_MERGED','KEEP_SEPARATE','MANUAL_MERGED','MANUAL_REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_revision_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    before_json JSON NOT NULL,
    after_json JSON NOT NULL,
    changed_fields_json JSON NOT NULL,
    reason VARCHAR(500) NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_revision_logs_public_id (public_id),
    KEY idx_job_revision_logs_job_time (job_id, created_at DESC),
    CONSTRAINT fk_job_revision_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_revision_logs_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO skills (public_id, canonical_name, display_name, category, description, status, created_at, updated_at, version) VALUES
('01M1E000000000000000000001', 'spring_cloud_alibaba', 'Spring Cloud Alibaba', 'FRAMEWORK', 'Spring Cloud Alibaba ecosystem', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
('01M1E000000000000000000002', 'rocketmq', 'RocketMQ', 'MESSAGE_QUEUE', 'Apache RocketMQ message broker', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
('01M1E000000000000000000003', 'kubernetes', 'Kubernetes', 'DEVOPS', 'Container orchestration platform', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
('01M1E000000000000000000004', 'large_language_model', 'Large Language Model', 'AI', 'Large language models', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
('01M1E000000000000000000005', 'langchain', 'LangChain', 'AI', 'LLM application framework', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
('01M1E000000000000000000006', 'vue', 'Vue', 'FRONTEND', 'Vue frontend framework', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

INSERT INTO skill_aliases (public_id, skill_id, alias_name, normalized_alias, source, confidence, active, created_at, updated_at, version)
SELECT CONCAT('01M1EA', LPAD(ROW_NUMBER() OVER (ORDER BY a.normalized_alias), 20, '0')),
       s.id, a.alias_name, a.normalized_alias, 'SYSTEM', 1.000, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
FROM (
    SELECT 'SpringBoot' alias_name, 'springboot' normalized_alias, 'spring_boot' canonical_name UNION ALL
    SELECT 'Spring Boot', 'springboot', 'spring_boot' UNION ALL
    SELECT 'Spring Cloud Alibaba', 'springcloudalibaba', 'spring_cloud_alibaba' UNION ALL
    SELECT 'MySQL8', 'mysql8', 'mysql' UNION ALL
    SELECT 'MySQL', 'mysql', 'mysql' UNION ALL
    SELECT 'Redis Cluster', 'rediscluster', 'redis' UNION ALL
    SELECT 'Redis', 'redis', 'redis' UNION ALL
    SELECT 'RocketMQ', 'rocketmq', 'rocketmq' UNION ALL
    SELECT 'Kafka', 'kafka', 'kafka' UNION ALL
    SELECT 'K8s', 'k8s', 'kubernetes' UNION ALL
    SELECT 'Kubernetes', 'kubernetes', 'kubernetes' UNION ALL
    SELECT 'Docker', 'docker', 'docker' UNION ALL
    SELECT 'LLM', 'llm', 'large_language_model' UNION ALL
    SELECT '大模型', '大模型', 'large_language_model' UNION ALL
    SELECT 'RAG', 'rag', 'rag' UNION ALL
    SELECT 'LangChain', 'langchain', 'langchain' UNION ALL
    SELECT 'LangGraph', 'langgraph', 'langgraph' UNION ALL
    SELECT 'Vue3', 'vue3', 'vue_3' UNION ALL
    SELECT 'Vue.js', 'vuejs', 'vue' UNION ALL
    SELECT 'ES6', 'es6', 'javascript' UNION ALL
    SELECT 'Java', 'java', 'java'
) a
JOIN skills s ON s.canonical_name = a.canonical_name
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- ════════════════════════════════════════════════════════════════════
-- AI 智能招聘系统 — 核心表 schema.sql (复刻自项目技术文档 §3.1)
-- PostgreSQL 16 + pgvector 0.8.5 + pg_trgm
-- ════════════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─────────────────── 3.1.1 resume 简历表 ───────────────────
CREATE TABLE IF NOT EXISTS resume (
    id              BIGSERIAL PRIMARY KEY,
    candidate_name  VARCHAR(100),
    raw_text        TEXT,
    parsed_json     JSONB,
    embedding       VECTOR(1024),
    risk_tags       TEXT[],
    status          VARCHAR(20) DEFAULT 'pending',   -- pending/reviewed/rejected
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_resume_status    ON resume (status);
CREATE INDEX IF NOT EXISTS idx_resume_embedding ON resume USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_resume_name_trgm ON resume USING gin (candidate_name gin_trgm_ops);

-- ─────────────────── 3.1.2 job_profile 岗位画像表 ───────────────────
CREATE TABLE IF NOT EXISTS job_profile (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200),
    jd_text         TEXT,
    department      VARCHAR(100),
    level           VARCHAR(50),
    location        VARCHAR(100),
    salary_min      INT,
    salary_max      INT,
    experience_min  INT,
    experience_max  INT,
    education       VARCHAR(50),
    headcount       INT,
    category        VARCHAR(100),
    weight_matrix   JSONB,
    role_graph      JSONB,
    growth_path     JSONB,
    embedding       VECTOR(1024),
    status          VARCHAR(20) DEFAULT 'draft',     -- draft/active/closed
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_job_status    ON job_profile (status);
CREATE INDEX IF NOT EXISTS idx_job_embedding ON job_profile USING hnsw (embedding vector_cosine_ops);

-- ─────────────────── 3.1.3 candidate_match 候选人匹配表 ───────────────────
CREATE TABLE IF NOT EXISTS candidate_match (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT REFERENCES job_profile (id) ON DELETE CASCADE,
    resume_id       BIGINT REFERENCES resume (id) ON DELETE CASCADE,
    overall_score   DECIMAL(5,2),
    skill_score     DECIMAL(5,2),
    experience_score DECIMAL(5,2),
    soft_score      DECIMAL(5,2),
    vector_score    DECIMAL(5,2),
    match_details   JSONB,
    hr_feedback     TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_match_job    ON candidate_match (job_id);
CREATE INDEX IF NOT EXISTS idx_match_resume ON candidate_match (resume_id);

-- ─────────────────── 3.1.4 memory_entry 长期记忆表 ───────────────────
CREATE TABLE IF NOT EXISTS memory_entry (
    id              BIGSERIAL PRIMARY KEY,
    agent_id        VARCHAR(64),                     -- 格式 hr:{userId}
    memory_key      VARCHAR(255),
    memory_value    TEXT,
    category        VARCHAR(50) DEFAULT 'note',      -- preference/fact/note/archived
    tags            TEXT[],
    access_count    INTEGER DEFAULT 0,
    last_access     TIMESTAMP,
    importance      DOUBLE PRECISION DEFAULT 0.5,
    embedding       VECTOR(1024),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_id, memory_key)
);
CREATE INDEX IF NOT EXISTS idx_memory_agent    ON memory_entry (agent_id);
CREATE INDEX IF NOT EXISTS idx_memory_category ON memory_entry (agent_id, category);
CREATE INDEX IF NOT EXISTS idx_memory_embedding ON memory_entry USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_memory_value_trgm ON memory_entry USING gin (memory_value gin_trgm_ops);

-- ─────────────────── 3.1.5 memory_graph 记忆图谱边表 ───────────────────
CREATE TABLE IF NOT EXISTS memory_graph (
    source_entry_id BIGINT REFERENCES memory_entry (id) ON DELETE CASCADE,
    target_entry_id BIGINT REFERENCES memory_entry (id) ON DELETE CASCADE,
    agent_id        VARCHAR(64),
    relation_type   VARCHAR(50) DEFAULT 'related_to',
    weight          DOUBLE PRECISION DEFAULT 1.0,
    PRIMARY KEY (source_entry_id, target_entry_id, relation_type)
);
CREATE INDEX IF NOT EXISTS idx_graph_source ON memory_graph (source_entry_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_graph_target ON memory_graph (target_entry_id, agent_id);

-- ─────────────────── 3.1.6 document_chunk 文档语义分块表 ───────────────────
CREATE TABLE IF NOT EXISTS document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    parent_type     VARCHAR(20),                     -- resume/job
    parent_id       BIGINT,
    chunk_index     INT,
    chunk_type      VARCHAR(50),                     -- skill/experience/education/summary
    content         TEXT,
    embedding       VECTOR(1024),
    UNIQUE (parent_type, parent_id, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_chunk_parent   ON document_chunk (parent_type, parent_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- ─────────────────── interview 面试记录 ───────────────────
CREATE TABLE IF NOT EXISTS interview (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT REFERENCES job_profile (id),
    resume_id       BIGINT REFERENCES resume (id),
    round           INT DEFAULT 1,
    status          VARCHAR(20) DEFAULT 'pending',   -- pending/scheduled/completed/cancelled
    interviewer     VARCHAR(100),
    scheduled_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── question 面试题 ───────────────────
CREATE TABLE IF NOT EXISTS question (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT REFERENCES interview (id) ON DELETE CASCADE,
    type            VARCHAR(50),                     -- technical/behavioral/project
    content         TEXT,
    follow_ups      JSONB,
    hr_adopted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_question_interview ON question (interview_id);

-- ─────────────────── interview_session AI 面试对话 ───────────────────
CREATE TABLE IF NOT EXISTS interview_session (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT REFERENCES interview (id) ON DELETE CASCADE,
    messages        JSONB,
    current_round   INT DEFAULT 1,
    difficulty_level VARCHAR(20) DEFAULT 'medium',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── interview_report AI 面试报告 ───────────────────
CREATE TABLE IF NOT EXISTS interview_report (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT REFERENCES interview (id) ON DELETE CASCADE,
    overall_score   DECIMAL(5,2),
    tech_score      DECIMAL(5,2),
    comm_score      DECIMAL(5,2),
    problem_solving_score DECIMAL(5,2),
    culture_fit_score DECIMAL(5,2),
    strengths      TEXT[],
    risks          TEXT[],
    hiring_suggestion VARCHAR(50),
    summary        TEXT,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── interview_evaluation 面试评估 ───────────────────
CREATE TABLE IF NOT EXISTS interview_evaluation (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT REFERENCES interview (id) ON DELETE CASCADE,
    tech_score      DECIMAL(5,2),
    project_score   DECIMAL(5,2),
    comm_score      DECIMAL(5,2),
    learning_score  DECIMAL(5,2),
    culture_score   DECIMAL(5,2),
    tags            TEXT[],
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── outreach 候选人触达 ───────────────────
CREATE TABLE IF NOT EXISTS outreach (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT REFERENCES job_profile (id),
    resume_id       BIGINT REFERENCES resume (id),
    message         TEXT,
    status          VARCHAR(20) DEFAULT 'draft',     -- draft/sent/replied/ignored
    batch_id        VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outreach_batch ON outreach (batch_id);

-- ─────────────────── hr_preference HR 偏好 ───────────────────
CREATE TABLE IF NOT EXISTS hr_preference (
    hr_id           BIGINT PRIMARY KEY,
    preference_json JSONB,
    expire_at        TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── agent_trace Agent 追踪 ───────────────────
CREATE TABLE IF NOT EXISTS agent_trace (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64),
    agent_name      VARCHAR(100),
    step_no         INT,
    step_type       VARCHAR(50),                      -- thinking/tool_call/tool_result/text
    tool_name       VARCHAR(100),
    input_text      TEXT,
    output_text     TEXT,
    model           VARCHAR(100),
    tokens          INT,
    latency_ms      BIGINT,
    status          VARCHAR(20) DEFAULT 'success',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_trace_session ON agent_trace (session_id);

-- ─────────────────── consolidation_task 记忆巩固任务队列 ───────────────────
CREATE TABLE IF NOT EXISTS consolidation_task (
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(20) DEFAULT 'pending',   -- pending/processing/completed/failed
    entry_ids       BIGINT[],
    result          JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── evaluation_golden_sample 评估金标样本 ───────────────────
CREATE TABLE IF NOT EXISTS evaluation_golden_sample (
    id              BIGSERIAL PRIMARY KEY,
    category        VARCHAR(100),
    input_text      TEXT,
    expected_output TEXT,
    criteria_json   JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── evaluation_result 评估结果 ───────────────────
CREATE TABLE IF NOT EXISTS evaluation_result (
    id              BIGSERIAL PRIMARY KEY,
    sample_id       BIGINT REFERENCES evaluation_golden_sample (id),
    actual_output   TEXT,
    score           DOUBLE PRECISION,
    details_json    JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── chat_session 聊天会话 ───────────────────
CREATE TABLE IF NOT EXISTS chat_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(200),
    agent_id        VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_session_user ON chat_session (user_id);

-- ─────────────────── chat_message 聊天消息 ───────────────────
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT REFERENCES chat_session (id) ON DELETE CASCADE,
    role            VARCHAR(20),                     -- user/assistant/system
    content         TEXT,
    tokens          INT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message (session_id);

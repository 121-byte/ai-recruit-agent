-- ════════════════════════════════════════════════════════════════════
-- AI 智能招聘系统 — 认证表 schema_auth.sql (复刻自项目技术文档 §3.2)
-- ════════════════════════════════════════════════════════════════════

-- ─────────────────── sys_user 用户表 ───────────────────
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(100) NOT NULL,            -- BCrypt 哈希
    real_name       VARCHAR(100),
    email           VARCHAR(100),
    phone           VARCHAR(20),
    department      VARCHAR(100),
    status          VARCHAR(20) DEFAULT 'active',     -- active/disabled
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────── sys_role 角色表 ───────────────────
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,      -- HR / OPS
    name            VARCHAR(100),
    description     VARCHAR(255)
);

-- ─────────────────── sys_user_role 用户角色关联表 ───────────────────
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id         BIGINT NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    role_id         BIGINT NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ════════════════════════════════════════════════════════════════════
-- 种子数据
-- ════════════════════════════════════════════════════════════════════

INSERT INTO sys_role (code, name, description) VALUES
    ('HR',  '招聘负责人', '负责招聘全流程操作: 岗位/简历/匹配/面试/触达'),
    ('OPS', '运营人员',   '负责岗位分析与候选人检索')
ON CONFLICT (code) DO NOTHING;

-- 密码 123456 的 BCrypt 哈希 (Spring Security BCryptPasswordEncoder, 已验证)
INSERT INTO sys_user (username, password, real_name, email, department) VALUES
    ('hr_user',  '$2a$10$HVZsXYup9tUX5FUocrxFHe3DYLPPXpNGvLNPC9aANwea3/zRUzOV6', 'HR 张三', 'hr@example.com',  '招聘部'),
    ('ops_user', '$2a$10$HVZsXYup9tUX5FUocrxFHe3DYLPPXpNGvLNPC9aANwea3/zRUzOV6', 'OPS 李四', 'ops@example.com', '运营部')
ON CONFLICT (username) DO NOTHING;

-- 关联: hr_user -> HR, ops_user -> OPS
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'hr_user'  AND r.code = 'HR'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'ops_user' AND r.code = 'OPS'
ON CONFLICT DO NOTHING;

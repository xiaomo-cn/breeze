-- ============================================================
-- V1__init.sql — Breeze 完整初始化
-- ============================================================
-- 设计决策：
--   - 无数据库外键约束（参照完整性在应用层管理）
--   - pgvector HNSW 索引用于语义搜索
--   - JSONB 用于灵活字段（AI 上下文、活动日志、工具参数）
--   - 软删除（is_deleted）用于任务
-- ============================================================

-- ============================================================
-- Part 1: 扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Part 2: 核心表
-- ============================================================

-- 2.1 用户
CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL UNIQUE,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    display_name         VARCHAR(100),
    avatar_url           VARCHAR(500),
    title                VARCHAR(100),
    department           VARCHAR(100),
    position_id          BIGINT,
    role                 VARCHAR(20)  DEFAULT 'user',
    must_change_password BOOLEAN      DEFAULT FALSE,
    timezone             VARCHAR(50)  DEFAULT 'Asia/Shanghai',
    locale               VARCHAR(10)  DEFAULT 'zh-CN',
    is_active            BOOLEAN      DEFAULT TRUE,
    created_at           TIMESTAMP    DEFAULT now(),
    updated_at           TIMESTAMP    DEFAULT now()
);

COMMENT ON COLUMN users.role IS '系统级角色: system_admin, user';
COMMENT ON COLUMN users.must_change_password IS '是否需要在下次登录时强制修改密码';

-- 2.2 职务/岗位定义
CREATE TABLE positions (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL UNIQUE,
    color      VARCHAR(20)  NOT NULL DEFAULT '#1677ff',
    sort_order INT          DEFAULT 0,
    created_at TIMESTAMP    DEFAULT now()
);

COMMENT ON TABLE  positions      IS '职务/岗位定义表';
COMMENT ON COLUMN positions.name IS '职务名称，如：前端开发、后端开发';
COMMENT ON COLUMN positions.color IS '颜色 hex 值，如：#1677ff、#52c41a';

-- 2.3 项目
CREATE TABLE projects (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    key             VARCHAR(10)  NOT NULL UNIQUE,
    description     TEXT,
    icon_url        VARCHAR(500),
    status          VARCHAR(20)  DEFAULT 'active',
    visibility      VARCHAR(20)  DEFAULT 'private',
    owner_id        BIGINT       NOT NULL,
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now()
);

-- 2.4 项目成员
CREATE TABLE project_members (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  DEFAULT 'member',
    joined_at       TIMESTAMP    DEFAULT now(),
    UNIQUE (project_id, user_id)
);

COMMENT ON COLUMN project_members.role IS '项目内角色: admin, manager, member, viewer';

-- 2.5 任务
CREATE TABLE tasks (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    parent_id        BIGINT,
    key              VARCHAR(20),
    title            VARCHAR(500) NOT NULL,
    description      TEXT,
    type             VARCHAR(20)  DEFAULT 'task',
    status           VARCHAR(20)  DEFAULT 'todo',
    priority         VARCHAR(20)  DEFAULT 'medium',
    assignee_id      BIGINT,
    reporter_id      BIGINT,
    sprint_id        BIGINT,
    story_points     INT,
    estimated_hours  DECIMAL(6,1),
    logged_hours     DECIMAL(6,1),
    due_date         DATE,
    started_at       TIMESTAMP,
    resolved_at      TIMESTAMP,
    sort_order       INT          DEFAULT 0,
    kanban_column_id BIGINT,
    risk_level       VARCHAR(10),
    risk_reason      TEXT,
    search_vector    tsvector,
    is_deleted       BOOLEAN      DEFAULT FALSE,
    created_at       TIMESTAMP    DEFAULT now(),
    updated_at       TIMESTAMP    DEFAULT now()
);

-- 全文搜索触发器
CREATE OR REPLACE FUNCTION tasks_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.key, '')), 'A');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tasks_search_vector
    BEFORE INSERT OR UPDATE OF title, description, key ON tasks
    FOR EACH ROW EXECUTE FUNCTION tasks_search_vector_update();

-- 2.6 任务协作者
CREATE TABLE task_collaborators (
    task_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    PRIMARY KEY (task_id, user_id)
);

COMMENT ON TABLE  task_collaborators IS '任务协作人关联表';

-- ============================================================
-- Part 3: 业务表
-- ============================================================

-- 3.1 任务评论（嵌套回复）
CREATE TABLE task_comments (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT       NOT NULL,
    parent_id       BIGINT,
    user_id         BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now()
);

-- 3.2 任务附件
CREATE TABLE task_attachments (
    id               BIGSERIAL PRIMARY KEY,
    task_id          BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    file_name        VARCHAR(500) NOT NULL,
    file_size        BIGINT       NOT NULL,
    content_type     VARCHAR(200),
    storage_key      VARCHAR(500) NOT NULL,
    storage_provider VARCHAR(20)  DEFAULT 'local' NOT NULL,
    created_at       TIMESTAMP    DEFAULT now()
);

-- 3.3 任务标签
CREATE TABLE task_tags (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    color           VARCHAR(20)  DEFAULT '#6366F1',
    created_at      TIMESTAMP    DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE task_tag_mappings (
    task_id         BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL,
    PRIMARY KEY (task_id, tag_id)
);

-- 3.4 任务依赖
CREATE TABLE task_dependencies (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT       NOT NULL,
    depends_on_task_id  BIGINT       NOT NULL,
    type                VARCHAR(20)  DEFAULT 'blocks',
    created_at          TIMESTAMP    DEFAULT now(),
    UNIQUE (task_id, depends_on_task_id)
);

-- 3.5 Sprint
CREATE TABLE sprints (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    goal            TEXT,
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(20)  DEFAULT 'planning',
    sort_order      INT          DEFAULT 0,
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now()
);

-- 3.6 看板
CREATE TABLE kanban_boards (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_default      BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT now()
);

CREATE TABLE kanban_columns (
    id              BIGSERIAL PRIMARY KEY,
    board_id        BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status_mapping  VARCHAR(50)  NOT NULL,
    wip_limit       INT          DEFAULT 0,
    sort_order      INT          DEFAULT 0,
    color           VARCHAR(20)  DEFAULT '#808080',
    created_at      TIMESTAMP    DEFAULT now()
);

-- 3.7 通知
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    body            TEXT,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    is_read         BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT now()
);

-- 3.8 操作审计日志
CREATE TABLE activity_log (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT,
    user_id         BIGINT,
    action_type     VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       BIGINT,
    details         JSONB,
    created_at      TIMESTAMP    DEFAULT now()
);

-- ============================================================
-- Part 4: AI 相关表
-- ============================================================

-- 4.1 AI 对话
CREATE TABLE ai_conversations (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    project_id       BIGINT,
    title            VARCHAR(500),
    model            VARCHAR(50),
    context_snapshot JSONB,
    created_at       TIMESTAMP    DEFAULT now(),
    updated_at       TIMESTAMP    DEFAULT now()
);

-- 4.2 AI 消息（精简版，工具调用日志由 ai_tool_executions 承担）
CREATE TABLE ai_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT,
    token_count     INT,
    metadata        JSONB,
    created_at      TIMESTAMP    DEFAULT now()
);

-- 4.3 AI 工具执行日志
CREATE TABLE ai_tool_executions (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT,
    message_id          BIGINT,
    user_id             BIGINT,
    tool_name           VARCHAR(100) NOT NULL,
    tool_input          JSONB,
    tool_output         JSONB,
    status              VARCHAR(20)  DEFAULT 'success',
    duration_ms         INT,
    created_at          TIMESTAMP    DEFAULT now()
);

-- 4.4 待确认的工具操作（写操作确认机制）
CREATE TABLE pending_tool_actions (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT        NOT NULL,
    tool_name       VARCHAR(100)  NOT NULL,
    description     VARCHAR(500)  NOT NULL,
    params_json     JSONB         NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP
);

-- 4.5 AI 报告
CREATE TABLE ai_reports (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    title           VARCHAR(500),
    content         TEXT         NOT NULL,
    generated_at    TIMESTAMP    DEFAULT now(),
    created_at      TIMESTAMP    DEFAULT now()
);

-- 4.6 pgvector 向量存储（语义搜索 RAG）
CREATE TABLE vector_store (
    id          VARCHAR(36) PRIMARY KEY,
    content     TEXT,
    metadata    JSONB,
    embedding   vector(1024)
);

-- ============================================================
-- Part 5: 索引
-- ============================================================

-- 用户
CREATE INDEX idx_users_email     ON users(email);
CREATE INDEX idx_users_active    ON users(is_active);
CREATE INDEX idx_users_role      ON users(role);
CREATE INDEX idx_users_position  ON users(position_id);

-- 项目成员
CREATE INDEX idx_members_user    ON project_members(user_id);

-- 任务
CREATE INDEX idx_tasks_project    ON tasks(project_id);
CREATE INDEX idx_tasks_assignee   ON tasks(assignee_id);
CREATE INDEX idx_tasks_status     ON tasks(project_id, status);
CREATE INDEX idx_tasks_priority   ON tasks(project_id, priority);
CREATE INDEX idx_tasks_type       ON tasks(project_id, type);
CREATE INDEX idx_tasks_due_date   ON tasks(assignee_id, due_date);
CREATE INDEX idx_tasks_sprint     ON tasks(sprint_id);
CREATE INDEX idx_tasks_parent     ON tasks(parent_id);
CREATE INDEX idx_tasks_created    ON tasks(project_id, created_at DESC);
CREATE INDEX idx_tasks_sort       ON tasks(kanban_column_id, sort_order);
CREATE INDEX idx_tasks_search     ON tasks USING GIN (search_vector);

-- 任务协作者
CREATE INDEX idx_tc_user_id       ON task_collaborators(user_id);

-- 评论
CREATE INDEX idx_comments_task    ON task_comments(task_id, created_at);
CREATE INDEX idx_comments_parent  ON task_comments(parent_id);

-- 附件
CREATE INDEX idx_attachments_task     ON task_attachments(task_id);
CREATE INDEX idx_attachments_provider ON task_attachments(storage_provider);

-- 依赖
CREATE INDEX idx_dep_task        ON task_dependencies(task_id);
CREATE INDEX idx_dep_depends_on  ON task_dependencies(depends_on_task_id);

-- Sprint
CREATE INDEX idx_sprints_project ON sprints(project_id, status);

-- 看板
CREATE INDEX idx_boards_project  ON kanban_boards(project_id);
CREATE INDEX idx_columns_board   ON kanban_columns(board_id, sort_order);

-- 通知
CREATE INDEX idx_notif_user      ON notifications(user_id, is_read, created_at);
CREATE INDEX idx_notif_unread    ON notifications(user_id, created_at DESC) WHERE is_read = FALSE;

-- 活动日志
CREATE INDEX idx_activity_project ON activity_log(project_id, created_at);
CREATE INDEX idx_activity_user    ON activity_log(user_id, created_at);

-- AI
CREATE INDEX idx_ai_conv_user      ON ai_conversations(user_id, project_id);
CREATE INDEX idx_ai_msg_conv       ON ai_messages(conversation_id, created_at);
CREATE INDEX idx_ai_tool_conv      ON ai_tool_executions(conversation_id);
CREATE INDEX idx_ai_tool_user_conv ON ai_tool_executions(user_id, conversation_id);
CREATE INDEX idx_ai_reports_pj     ON ai_reports(project_id, type, generated_at DESC);
CREATE INDEX idx_pending_status    ON pending_tool_actions(status, created_at);

-- pgvector HNSW 索引（余弦相似度搜索）
CREATE INDEX idx_vector_hnsw ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- Part 6: 种子数据
-- ============================================================

-- 6.1 默认职务
INSERT INTO positions (name, color, sort_order) VALUES
    ('前端开发',     '#1677ff', 1),
    ('后端开发',     '#52c41a', 2),
    ('全栈开发',     '#722ed1', 3),
    ('产品经理',     '#fa8c16', 4),
    ('UI设计师',     '#eb2f96', 5),
    ('测试工程师',   '#13c8cf', 6),
    ('DevOps',       '#fa541c', 7),
    ('技术负责人',   '#faad14', 8);

-- 6.2 默认看板列（通过应用层创建项目时自动初始化，此处仅作为参考）
-- 应用层 ProjectService.create() 会自动为每个新项目创建默认看板和 6 列

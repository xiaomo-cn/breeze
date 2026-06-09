-- ============================================================
-- V2__knowledge_base.sql — 企业知识库
-- ============================================================
-- 设计决策：
--   - parent_folder_id 自引用实现不限层级文件夹
--   - 子项默认继承父文件夹权限（可单独覆盖）
--   - 切片向量复用 vector_store 表，通过 metadata.doc_type 区分
--   - 知识库对话与项目 AI 对话完全独立
-- ============================================================

-- ============================================================
-- Part 1: 知识库核心表
-- ============================================================

-- 1.1 知识文档 / 文件夹
CREATE TABLE knowledge_documents (
    id                BIGSERIAL PRIMARY KEY,
    parent_folder_id  BIGINT,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    file_name         VARCHAR(500),
    file_type         VARCHAR(50)  NOT NULL DEFAULT 'folder',  -- folder, pdf, docx, xlsx, pptx, md, txt, png, jpg, html, csv
    file_size         BIGINT       DEFAULT 0,
    file_hash         VARCHAR(64),                              -- SHA-256，文件级去重
    storage_key       VARCHAR(500),                             -- 文件存储路径
    chunk_count       INT          DEFAULT 0,                   -- 切片数量
    embedding_status  VARCHAR(20)  DEFAULT 'pending',           -- pending, processing, completed, failed
    created_by        BIGINT       NOT NULL,
    updated_by        BIGINT,
    created_at        TIMESTAMP    DEFAULT now(),
    updated_at        TIMESTAMP    DEFAULT now(),
    is_deleted        BOOLEAN      DEFAULT FALSE
);

COMMENT ON TABLE  knowledge_documents                IS '知识库文档与文件夹表';
COMMENT ON COLUMN knowledge_documents.parent_folder_id IS '父文件夹 ID，NULL 表示根目录';
COMMENT ON COLUMN knowledge_documents.file_type      IS 'folder / pdf / docx / xlsx / pptx / md / txt / png / jpg / html / csv';
COMMENT ON COLUMN knowledge_documents.file_hash      IS 'SHA-256 文件哈希，用于去重';
COMMENT ON COLUMN knowledge_documents.chunk_count    IS '向量切片数量';
COMMENT ON COLUMN knowledge_documents.embedding_status IS 'pending / processing / completed / failed';

-- 1.2 标签（全局共享）
CREATE TABLE knowledge_tags (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    color      VARCHAR(20)  DEFAULT '#1677ff',
    created_at TIMESTAMP    DEFAULT now()
);

COMMENT ON TABLE  knowledge_tags      IS '知识库标签表';
COMMENT ON COLUMN knowledge_tags.name IS '标签名称，全局唯一';

-- 1.3 文档-标签关联（多对多）
CREATE TABLE knowledge_document_tags (
    document_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (document_id, tag_id)
);

COMMENT ON TABLE knowledge_document_tags IS '文档与标签多对多关联表';

-- 1.4 文档权限
CREATE TABLE knowledge_document_permissions (
    id          BIGSERIAL   PRIMARY KEY,
    document_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    permission  VARCHAR(20) NOT NULL,        -- read, manage
    granted_by  BIGINT      NOT NULL,
    created_at  TIMESTAMP   DEFAULT now(),
    UNIQUE (document_id, user_id)
);

COMMENT ON TABLE  knowledge_document_permissions             IS '知识库文档权限表';
COMMENT ON COLUMN knowledge_document_permissions.permission IS 'read 或 manage';

-- ============================================================
-- Part 2: 知识库 AI 对话表
-- ============================================================

-- 2.1 知识库对话
CREATE TABLE knowledge_conversations (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(500) DEFAULT '新对话',
    model      VARCHAR(100) NOT NULL DEFAULT 'qwen-vl-max',
    created_at TIMESTAMP    DEFAULT now(),
    updated_at TIMESTAMP
);

COMMENT ON TABLE  knowledge_conversations       IS '知识库 AI 问答对话表';
COMMENT ON COLUMN knowledge_conversations.model IS '使用的多模态模型';

-- 2.2 知识库消息
CREATE TABLE knowledge_messages (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,      -- user, assistant
    content         TEXT,
    referenced_docs JSONB,                      -- [{id, title, file_type, page_number}]
    token_count     INT,
    created_at      TIMESTAMP    DEFAULT now()
);

COMMENT ON TABLE  knowledge_messages                IS '知识库 AI 问答消息表';
COMMENT ON COLUMN knowledge_messages.referenced_docs IS '本次回答引用的文档列表 JSON';

-- ============================================================
-- Part 3: 索引
-- ============================================================

-- 知识文档
CREATE INDEX idx_kd_parent         ON knowledge_documents(parent_folder_id) WHERE NOT is_deleted;
CREATE INDEX idx_kd_file_hash      ON knowledge_documents(file_hash) WHERE file_hash IS NOT NULL AND NOT is_deleted;
CREATE INDEX idx_kd_file_type      ON knowledge_documents(file_type) WHERE NOT is_deleted;
CREATE INDEX idx_kd_embedding      ON knowledge_documents(embedding_status) WHERE NOT is_deleted;
CREATE INDEX idx_kd_created_by     ON knowledge_documents(created_by);
CREATE INDEX idx_kd_updated_at     ON knowledge_documents(updated_at DESC) WHERE NOT is_deleted;

-- 标签
CREATE INDEX idx_kt_name           ON knowledge_tags(name);

-- 权限
CREATE INDEX idx_kdp_doc           ON knowledge_document_permissions(document_id);
CREATE INDEX idx_kdp_user_perm     ON knowledge_document_permissions(user_id, permission);

-- 对话
CREATE INDEX idx_kc_user           ON knowledge_conversations(user_id, updated_at DESC);

-- 消息
CREATE INDEX idx_km_conv           ON knowledge_messages(conversation_id, created_at);

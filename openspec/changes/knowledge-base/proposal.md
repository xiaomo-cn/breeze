## Why

Breeze 作为团队项目管理系统，缺少一个关键能力：**让 AI 基于企业已有的技术文档、产品需求、流程规范来回答问题**。目前 AI 助手只能检索项目内的任务数据，无法回答"接口规范是什么""数据库命名标准是什么"这类知识型问题。同时团队需要导入 PDF、Word、Markdown 等多种格式的文档，需要一个轻量的、AI 原生的知识库——而不是另一个 Confluence。

## What Changes

- 新增**组织级知识库**模块，独立于项目，通过侧栏导航入口访问
- 支持上传多种格式文档（PDF、Word、PPT、Excel、Markdown、PNG、HTML、CSV、TXT），**无需 OCR 或文本提取**
- 采用**多模态 RAG 架构**：`qwen3-vl-embedding` 对整个文件做多模态向量化，`qwen-vl-max` 直接"看"原文件回答
- 文档哈希去重（SHA-256），避免重复上传
- 标签系统（多标签筛选），不做文件夹层级
- 文档级权限控制（read / manage）
- 独立的知识库 AI 问答面板，SSE 流式输出，引用来源文档
- 知识库对话历史独立存储，不影响项目 AI 对话
- 文件存储复用现有 `FileStorageService`（Local / S3 / MinIO）
- 向量存储复用现有 `vector_store` 表，通过 `metadata.doc_type` 区分来源

## Capabilities

### New Capabilities

- `knowledge-document-management`: 文档上传、标签管理、搜索、权限控制、去重
- `knowledge-ai-chat`: 多模态 AI 问答、SSE 流式推送、对话历史、来源引用
- `knowledge-embedding`: 多模态向量化管线，文件摄入时异步生成向量

### Modified Capabilities

<!-- 本次变更不修改已有 capabilities -->

## Impact

- **后端新增包**：`cn.xiaomo.breeze.knowledge/`（controller、service、mapper、entity、dto、config）
- **新增依赖**：无新增 Maven 依赖（多模态 Embedding + Chat 均通过 HTTP API 调用百炼）
- **数据库新增表**：`knowledge_documents`、`knowledge_document_permissions`、`knowledge_conversations`、`knowledge_messages`、`knowledge_document_tags`（Flyway V17）
- **新增 AI 配置**：第二个 `ChatClient` bean（百炼多模态）和第二个 `EmbeddingModel` bean（`qwen3-vl-embedding`），与现有 DeepSeek + `text-embedding-v3` 并存
- **前端新增**：`KnowledgeBasePage`、`KnowledgeChatPanel`、`KnowledgeUploadModal` 组件 + 路由 `/knowledge`
- **侧栏改动**：`Sidebar` 组件新增"知识库"导航项（与"仪表盘"同级）
- **文件存储**：复用 `FileStorageService`，新增 `knowledge/` 存储路径前缀

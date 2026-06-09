# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言偏好

所有回答和生成的文档使用中文，代码注释也使用中文。

## 项目概述

Breeze 是一个带 AI Agent 的团队项目管理系统（类 Jira/Linear），支持看板管理、Sprint 规划、甘特图、AI 智能助手。

**技术栈：**
- 后端：Spring Boot 3.3、MyBatis-Plus 3.5、Spring AI 1.1.7、PostgreSQL 16、Redis 7
- 前端：React 18 + TypeScript、Vite 5、Ant Design 5、Tailwind CSS、Zustand、@dnd-kit
- AI：DeepSeek V4 Pro，通过 Spring AI OpenAI starter 接入（OpenAI 兼容 API）

## 常用命令

```bash
# 后端
cd backend
mvn spring-boot:run                   # 启动开发服务器，端口 :8080
mvn compile                          # 仅编译
mvn test                             # 运行测试

# 前端
cd frontend
npm install                          # 安装依赖
npm run dev                          # 启动 Vite 开发服务器，端口 :5173（/api 代理到 :8080）
npx tsc --noEmit                     # 仅类型检查
npm run build                        # 生产构建

# 环境变量
export DEEPSEEK_API_KEY=sk-...       # AI 功能必需
export JWT_SECRET=...                # 可选，有开发默认值
export EMBEDDING_API_KEY=sk-...      # 语义搜索必需（OpenAI text-embedding-3-small）
export DASHSCOPE_API_KEY=sk-...     # 知识库向量化必需（阿里百炼 text-embedding-v4，兼容 OpenAI API）
```

## 基础设施

PostgreSQL 16 + Redis 7 需要本地安装运行：

- PostgreSQL：默认连接 `localhost:5432`，数据库 `breeze`，用户名/密码 `breeze/breeze123`
- Redis：默认连接 `localhost:6379`

## 架构

后端是单模块 Spring Boot 项目，按包分层：

```
cn.xiaomo.breeze
├── common/          # ApiError、PageDTO、GlobalExceptionHandler、JsonbTypeHandler、MybatisPlusConfig
├── auth/            # Spring Security + JWT (jjwt)、UserController、Refresh Token Redis 存储
├── project/         # 项目 CRUD + 成员管理
├── task/            # 任务 CRUD、搜索（全文+向量）、异步 Embedding、Redis 自增编号
├── kanban/          # 看板管理（列 CRUD + 状态映射 + WIP）
├── sprint/          # Sprint 管理（燃尽图 + 排期）
├── board/           # 看板数据层
├── gantt/           # 甘特图数据
├── report/          # 报表（日报/周报/Sprint + PDF/CSV 导出）
├── comment/         # 任务评论（嵌套回复）
├── attachment/      # 文件附件（Local + S3 双后端）
├── dependency/      # 任务依赖关系
├── notification/    # 通知系统（SSE 实时推送）
├── activity/        # 操作审计日志
├── event/           # SSE 实时事件（SseEmitterRegistry）
├── config/          # 存储配置、缓存配置
├── ai/              # Spring AI ChatClient、12 个 @Tool 方法、SSE 流式、限流、RAG、拆解/排期/风控/报告
└── knowledge/       # 知识库模块（文档管理、Tika 解析、向量化、RAG 问答）

**关键设计决策：**
- ORM：MyBatis-Plus（`BaseMapper<T>` + `LambdaQueryWrapper`）。复杂查询通过 XML Mapper（如搜索）。
- AI：Spring AI OpenAI starter 指向 `https://api.deepseek.com`。Embedding 使用独立 API：
  - 任务搜索：OpenAI `text-embedding-3-small`，异步更新到 pgvector
  - 知识库：阿里百炼 `text-embedding-v4`（兼容 OpenAI API，1024 维），独立 `KnowledgeAiConfig` 配置
- **无数据库外键约束**：参照完整性在应用层管理，避免 DDL 耦合和性能问题。
- JSONB 列使用自定义 `JsonbTypeHandler`——带 JSONB/JSON 字段的实体需要 `@TableName(autoResultMap = true)` + `@TableField(typeHandler = JsonbTypeHandler.class)`。
- 任务编号：Phase 1 使用 `T-{Redis INCR}`，后续改为 `{PROJECT_KEY}-{seq}`。
- 文件上传：Phase 3 才做，届时通过 `FileStorageService` 接口抽象 S3 兼容存储。
- @Async 已启用（`BreezeApplication`），用于 Embedding 异步更新。
- **事务管理：** 涉及多表写操作的方法必须加 `@Transactional(rollbackFor = Exception.class)`，确保异常时全部回滚。不能只用 `@Transactional`（默认只回滚 RuntimeException/Error，不回滚受检异常）。

### AI Agent 流程

```
POST /api/v1/ai/chat (SSE)
  → AiAgentService.streamChat()
    → RagService.buildRagContext()     # 项目信息 + 最近任务 + 用户任务
    → buildSystemPrompt()              # RAG 上下文 + 成员列表
    → loadConversationHistory()        # 最近 20 条消息
    → ChatClient.prompt().stream()     # DeepSeek V4 Pro，带 12 个工具
    → SSE 流式推送 → AiChatPanel（React，fetch + ReadableStream）
```

**12 个 AI 工具**（`TaskTools.java`、`ReadTools.java`、`WriteTools.java` 中的 `@Tool` 注解）：
- `create_task` / `search_tasks` / `list_members` — 任务创建、搜索、成员列表
- `get_task_detail` / `get_sprint_status` / `get_user_workload` — 只读查询
- `update_task` / `assign_task` / `add_comment` / `create_subtasks` / `add_to_sprint` — 写操作（需用户确认）
- 高级功能：任务拆解、智能排期、风险评估、报告生成、NL→SQL 查询

**限流：** `RateLimitFilter` — 每用户每分钟 20 条消息，基于 Redis INCR + TTL。

### 知识库模块

独立的组织级知识库，通过侧边栏「知识库」入口访问，与项目管理模块解耦。

**数据模型：**
- `knowledge_documents` — 文档/文件夹（`parent_folder_id` 自引用，无限层级），SHA-256 去重，软删除
- `knowledge_tags` — 全局标签（多对多关联文档）
- `knowledge_document_permissions` — 文档级权限（read / manage），子项继承父文件夹权限
- `knowledge_conversations` / `knowledge_messages` — AI 对话历史，与项目 AI 助手独立

**文档摄入流程：**
```
上传文件
  → SHA-256 去重检查
  → FileStorageService 存储（local/S3）
  → DocumentParser（TikaDocumentParser）提取文本
  → 全文存入 knowledge_documents.extracted_text
  → TokenTextSplitter / ParagraphSplitter 按文件类型自适应分块
  → 阿里百炼 text-embedding-v4 向量化（1024 维）
  → 写入 vector_store（metadata.doc_type = "knowledge_document"）
```

**知识库问答流程：**
```
POST /api/v1/knowledge/chat (SSE)
  → KnowledgeRetrievalService.retrieve()
    → 向量相似度搜索（doc_type 过滤）
    → 按 doc_id 聚合 + 权限过滤 + 相关性阈值（cosine ≥ 0.3）
    → 返回 topK=5 篇文档
  → KnowledgeChatService.streamChat()
    → 从 DB 读取 extracted_text（不重复解析文件）
    → 按 token 预算动态截断（总预算 32K）
    → 构建 RAG Prompt → DeepSeek ChatClient → SSE 流式回答
```

**关键配置（application.yml）：**
- `spring.ai.knowledge.embedding` — 独立的 embedding 配置（阿里百炼）
- `spring.ai.knowledge.splitter` — 分块参数（chunk-size=500, overlap=50, max-chunks=100）
- 知识库问答复用项目主 ChatClient（DeepSeek），不单独配置

### 搜索架构

```
GET /api/v1/projects/{pid}/search?q=&type=fulltext|semantic
    │
    ├── fulltext → PostgreSQL tsvector + GIN 索引 (plainto_tsquery)
    │
    └── semantic → pgvector HNSW 索引 + cosine 距离 (<->)
                    ↑ Embedding 由 OpenAI text-embedding-3-small 生成
                    ↑ @Async 异步更新 (TaskChangedEvent → TaskEventListener)
```

### 前端

- **API 层：** Axios 实例 + JWT 拦截器。401 → 自动通过 `/auth/refresh` 刷新，并发请求排队等待。
- **SSE：** `fetch` + `ReadableStream` + 手动行解析（不用 EventSource，因为不支持 POST）。
- **看板：** `@dnd-kit/core` + `@dnd-kit/sortable`。Zustand store 乐观更新 + API 失败回滚。
- **Tailwind：** `preflight: false`，避免覆盖 Ant Design 基础样式。

### 数据库

Flyway 迁移脚本在 `backend/src/main/resources/db/migration/`：

| 版本 | 内容 |
|------|------|
| V1 | 核心表：users、projects、project_members、tasks、task_embeddings、ai_conversations、ai_messages、vector_store |
| V2 | 知识库表：knowledge_documents、knowledge_tags、knowledge_document_tags、knowledge_document_permissions、knowledge_conversations、knowledge_messages |
| V2_1 | 知识库优化：knowledge_documents 新增 extracted_text 列 |
| V3 | 全文搜索：tsvector 列 + GIN 索引 + 触发器 + HNSW 索引 |
| V5 | 业务索引：复合索引覆盖常见查询 |
| V6 | task_embeddings 唯一约束（支持 upsert） |

## OpenSpec 工作流

本项目使用 OpenSpec 管理变更。变更记录在 `openspec/changes/`：

```
openspec/changes/
├── phase-0-quick-prototype/     # 已完成（23/27 任务）
├── phase-1-backend-mvp/         # 已完成（26/26）
├── phase-2-frontend-shell/      # 已完成
├── knowledge-base/              # 已完成（43/49 任务）
├── phase-3-collaboration/       # 待开始
├── phase-4-reports-gantt/       # 待开始
├── phase-5-ai-agent-core/       # 待开始
├── phase-6-ai-agent-advanced/   # 待开始
└── phase-7-polish-launch/      # 待开始
```

执行某个 Phase：`/opsx:apply <phase-name>`。每个 change 包含 proposal.md、design.md、specs/ 和 tasks.md。

## Spring AI 版本注意事项

当前使用 Spring AI 1.1.7（Maven Central，无需额外仓库），关键 API：
- `TokenTextSplitter` 在 `org.springframework.ai.transformer.splitter` 包下
- `PgVectorStore.builder(JdbcTemplate, EmbeddingModel)` 构造函数
- `SearchRequest.builder()` 模式进行向量检索
- `Document.getText()` 获取文档文本内容
- 知识库模块使用独立的 `OpenAiEmbeddingModel` Bean（`@Qualifier("knowledgeEmbeddingModel")`），指向阿里百炼 text-embedding-v4
- `ChatClient` 通过 Spring AI 自动配置，主项目和知识库共享同一个 DeepSeek ChatClient

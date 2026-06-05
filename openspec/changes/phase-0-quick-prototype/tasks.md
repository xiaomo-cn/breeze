# Phase 0: Tasks

## Backend

- [x] 初始化 Spring Boot 3.3 + Maven 项目，配置所有依赖（Spring AI、MyBatis-Plus、Security、pgvector）
- [x] 编写 docker-compose.yml：PostgreSQL 16 + Redis 7 + MinIO
- [x] 编写 Flyway 迁移脚本 V1__init.sql（users, projects, project_members, tasks, task_embeddings, ai_conversations, ai_messages）
- [x] 启用 pgvector 扩展 + 创建向量索引（HNSW）
- [x] 实现 JWT 认证：POST /api/v1/auth/register, /api/v1/auth/login, /api/v1/auth/refresh
- [x] 实现项目 CRUD：POST /api/v1/projects, GET /api/v1/projects, GET /api/v1/projects/{id}
- [x] 实现任务 CRUD：POST /api/v1/projects/{pid}/tasks, GET /api/v1/projects/{pid}/tasks, PATCH /api/v1/tasks/{id}
- [x] 实现 Spring AI ChatClient 配置（Claude API）
- [x] 实现 3 个 @Tool：create_task, search_tasks, list_members
- [x] 实现 RAG：pgvector 向量检索相关任务上下文
- [x] 实现 AI 对话 SSE 端点：POST /api/v1/ai/chat
- [x] 实现简单限流（20 条消息/分钟/用户）
- [x] 统一 API 错误处理 + 参数校验

## Frontend

- [x] 初始化 Vite + React + TypeScript 项目
- [x] 配置 Ant Design 5 + Tailwind CSS
- [x] 实现 LoginPage、RegisterPage
- [x] 实现 AppLayout（Sidebar + Header 骨架）
- [x] 实现 DashboardPage（项目列表 + 创建项目）
- [x] 实现 ProjectBoardPage（三列硬编码：Backlog / In Progress / Done）
- [x] 实现 TaskCard 组件 + TaskCreateModal
- [x] 实现 @dnd-kit 拖拽切换任务状态
- [x] 实现 AiChatPanel（右下角浮窗，SSE 流式渲染）
- [x] 实现 fetch + ReadableStream 处理 SSE

## Verification

- [ ] 浏览器注册 → 登录 → 创建项目
- [ ] 手动创建任务 → 看板拖拽切换状态
- [ ] AI 对话框输入"帮我创建一个任务：修复登录页面样式 bug，优先级高"
- [ ] AI 回复并自动创建任务，看板上出现新卡片

# Phase 0: Design

## Architecture

```
React SPA (Vite) ──▶ Nginx ──▶ Spring Boot 3.3 (8080)
                                    │
                      ┌─────────────┼─────────────┐
                      ▼             ▼             ▼
               PostgreSQL 16    Redis 7       MinIO
               (pgvector)       (Session)     (S3)
```

## Key Decisions

### 单模块 Maven 项目

Phase 0 不做多模块拆分，所有代码在一个 `backend/` 目录下。包结构按领域划分（auth/project/task/ai），为后续拆分做准备但不引入 Maven module 复杂度。

### AI 写工具暂不需要确认

快速原型阶段 `create_task` 直接执行，不经过确认流程。Phase 5 再加确认机制。这样可以先验证 Spring AI Tool Calling 的核心链路。

### RAG 用 pgvector 但范围最小化

仅对任务标题+描述建 Embedding 索引，不做增量更新——每次任务变更全量重建索引。Phase 1 再改为异步增量更新。

### 前端 SSE 用 fetch + ReadableStream

不用 EventSource（只支持 GET），直接用 fetch + ReadableStream 处理 POST 请求的 SSE 流式响应。

### 看板仅三列硬编码

Backlog / In Progress / Done，不做自定义列。Phase 2 再做完整的看板配置。

## Schema (minimal)

只建 Phase 0 需要的表：users, projects, project_members, tasks, task_embeddings, ai_conversations, ai_messages

完整表结构在 Phase 1 补齐。

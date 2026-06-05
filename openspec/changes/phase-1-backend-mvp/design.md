# Phase 1: Design

## Database Tables (Complete)

Phase 0 只建了核心表，Phase 1 补齐全部：

- task_comments — 任务评论（支持嵌套回复）
- task_attachments — 文件附件
- task_tags + task_tag_mappings — 标签系统
- task_dependencies — 任务依赖关系
- sprints — Sprint 管理
- kanban_boards + kanban_columns — 看板配置
- notifications — 通知系统
- ai_tool_executions — AI 工具调用日志
- activity_log — 操作审计日志

## Search Architecture

```
搜索请求
    │
    ├── 关键词搜索 → PostgreSQL tsvector + GIN 索引
    │                 (zhparser 中文分词 / pg_bigm)
    │
    └── 语义搜索   → pgvector HNSW 索引
                     cosine 相似度 (<->)
```

全文搜索和语义搜索可以组合使用：先用语义搜索找 Top-K，再在结果中用全文搜索精确匹配。

## Async Embedding Update

Phase 0 每次全量重建索引，Phase 1 改为：

```
Task 变更 → 发 ApplicationEvent
    → @Async @EventListener
        → 重新生成 Embedding
        → UPSERT task_embeddings
```

这样不影响请求响应时间，且只更新变更的任务。

## Refresh Token Flow

```
Access Token 过期 (1h)
    → 前端拦截 401
    → POST /api/v1/auth/refresh (body: refreshToken)
    → 后端验证 Refresh Token (7d)
    → 返回新的 Access Token + Refresh Token
    → 前端重试原请求
```

Refresh Token 存储在 Redis，支持主动失效（修改密码、退出登录）。

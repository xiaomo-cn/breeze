# Phase 1: Tasks

## Database

- [x] 编写 Flyway 迁移 V2-V5 覆盖全部表
- [x] 建立 tsvector 列 + GIN 索引（全文搜索）
- [x] 建立 task_embeddings HNSW 索引
- [x] 建立所有业务索引（外键由应用层管理，不在 DB 层设置）

## Auth

- [x] 实现 Refresh Token 端点 + Redis 存储
- [x] 实现 Refresh Token 轮换（每次刷新发放新 Token）
- [x] 实现登出（失效 Refresh Token）
- [x] JWT Filter 完善（路径白名单、Token 验证）

## CRUD Enhancement

- [x] 项目：完善分页/过滤/成员管理 API
- [x] 任务：完善过滤（status/priority/assignee/type）、排序、软删除
- [x] 用户：GET /users、GET /users/{id}、GET /users/suggestions
- [x] 统一分页响应格式（PageDTO<T>）
- [x] 统一错误响应格式（ApiError）

## Search

- [x] 实现 PostgreSQL 全文搜索（tsvector + plainto_tsquery）
- [x] 实现 pgvector 语义搜索（在 XML Mapper 中写向量 SQL）
- [x] 实现 @Async Embedding 异步更新
- [x] 搜索端点：GET /api/v1/projects/{pid}/search?q=&type=fulltext|semantic

## Testing

- [x] Auth API 集成测试（注册/登录/刷新/登出）
- [x] Project CRUD 集成测试
- [x] Task CRUD 集成测试
- [x] 全文搜索集成测试
- [x] 向量搜索集成测试

## Verification

- [x] 集成测试验证全套 API (19/19 通过，无需 Docker)
- [x] Refresh Token 流程正常 (注册/登录/刷新/轮换/登出全部通过)
- [x] 全文搜索返回正确结果 (tsvector + plainto_tsquery 正常)
- [x] 向量搜索端点就绪 (需 EMBEDDING_API_KEY 才能返回实际语义结果，测试已覆盖优雅降级)

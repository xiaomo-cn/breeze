# Phase 1: Backend MVP

## Why

Phase 0 验证了核心链路可行。Phase 1 在快速原型基础上完善后端基础设施，补齐所有数据库表、完善认证流程、建立全文搜索和向量索引，为前端开发提供完整的 API 基础。

## What

- 完善单模块项目结构，Flyway 迁移覆盖全部业务表
- JWT 完整认证流程（Refresh Token 自动刷新）
- 所有 CRUD API 完善（分页、过滤、排序）
- PostgreSQL 全文搜索（tsvector + GIN 索引）
- pgvector 向量索引 + 异步 Embedding 更新（不再全量重建）
- 统一 API 错误处理 + 参数校验
- 集成测试覆盖核心 API

## Impact

- 依赖：Phase 0 完成
- 新增：完整的数据库表结构
- 增强：认证流程（Refresh Token 机制）
- 增强：搜索能力（全文搜索 + 向量搜索）

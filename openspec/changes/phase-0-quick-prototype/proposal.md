# Phase 0: Quick Prototype

## Why

从零验证 Breeze 核心链路"注册 → 登录 → 创建项目 → 创建任务 → AI 自然语言创建任务"的技术可行性。用最小技术栈在 2-3 天内跑通全流程，降低后续投入风险。

## What

- Spring Boot 3.3 单模块后端，包含认证、项目 CRUD、任务 CRUD、AI 对话端点
- docker-compose 一键启动 PostgreSQL 16 + Redis 7 + MinIO
- React + Vite + Ant Design 5 前端，包含登录、Dashboard、简易三列看板、AI 对话浮窗
- Spring AI ChatClient + @Tool 注解实现 AI 自然语言创建任务
- pgvector 向量检索提供 RAG 上下文

## Impact

- 新增：整个后端项目骨架、前端项目骨架
- 新增：Flyway 迁移脚本（建表 + pgvector 扩展）
- 新增：AI 对话 SSE 端点
- 风险：pgvector + Spring AI 集成复杂度可能超预期

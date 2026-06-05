# Phase 5: AI Agent Core

## Why

Phase 0 的 AI 是最简版（3 个 Tool，无确认）。Phase 5 构建完整的 AI Agent 引擎：完善 Spring AI 编排、所有读写工具、工具调用确认机制、上下文管理、RAG 优化，打造真正的 AI 协管助手。

## What

- 完整的 AI Agent 编排器（ContextRetriever + Orchestrator）
- 全部读写工具（创建/更新/分配任务、批量子任务、评论、Sprint 操作等）
- 写工具确认机制（SSE tool_confirmation 事件 → 用户确认 → 执行）
- 上下文窗口管理（Token 预算 + 对话压缩 + RAG 分层）
- 对话历史管理（列表、搜索、删除）
- AI 端点限流完善（20 条消息/分钟/用户、5 次拆解/小时）
- 前端：AiChatPanel 流式渲染 + 工具确认 UI + 对话历史

## Impact

- 依赖：Phase 1-3（需要完整的任务、Sprint、评论模块）
- 增强：从"简单 AI 对话"升级到"AI 能操控项目管理系统"

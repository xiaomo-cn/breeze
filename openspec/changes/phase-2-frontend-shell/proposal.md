# Phase 2: Frontend Shell + Kanban

## Why

Phase 0 的前端是最小可用版本。Phase 2 构建完整的 React 前端架构：路由系统、状态管理、AppLayout、Dashboard 仪表盘、完整的看板（@dnd-kit 拖拽 + 自定义列）、任务详情弹窗、SSE 实时更新。

## What

- 完整的 React 前端架构（路由、状态管理、API 层）
- AppLayout（Sidebar 项目导航 + Header 用户菜单）
- Dashboard 仪表盘首页
- 完整看板页面（@dnd-kit 拖拽、自定义列、WIP 限制）
- TaskCard、TaskCreateForm、TaskDetailModal
- SSE 实时事件流，看板多用户实时更新
- AiChatPanel 浮窗（基于 Phase 0 的 SSE 方案）

## Impact

- 依赖：Phase 1（需要完整的后端 API）
- 新增：完整的前端框架和页面
- 从"能用"升级到"可日常使用"的交互体验

## Why

当前看板列定义完全硬编码在前端（`KANBAN_COLUMNS` 6 列、Sprint `STATUS_COLUMNS` 4 列），两套独立实现、列不一致、拖拽体验参差不齐。数据库 `kanban_boards` 和 `kanban_columns` 表（含 `color`、`wip_limit`、`status_mapping` 字段）自 V2 迁移就已存在但从未被使用。团队需要能按项目自定义看板列（名称、颜色、WIP 限制），而不是改前端代码。

## What Changes

- 后端新增 `KanbanBoard`、`KanbanColumn` 实体/Mapper/Service/Controller，激活已有的数据库表
- 创建项目时自动初始化默认看板（6 列，带颜色），取代前端硬编码常量
- 提供列的 CRUD API，带保护逻辑：核心列（status_mapping 为 todo/in_progress/done）锁定不可修改或删除，其余列支持增删改；删除列时强制指定任务迁移目标列
- **自定义 status 值**：新增列可自由定义 `status_mapping`（VARCHAR(20) 内任意值），不限于预设 6 个状态，真正实现按团队流程自定义
- 前端从后端加载列配置，列颜色通过 `ColorPicker` 选择，支持预设色 + 自定义色
- **BREAKING**：移除前端 `KANBAN_COLUMNS` 和 `STATUS_COLUMNS` 硬编码常量
- Sprint 详情页看板统一复用 `KanbanBoard`/`KanbanColumn`/`TaskCard` 组件，通过 `sprintId` prop 过滤数据
- 修复 Sprint 看板拖拽：补充 `useDroppable`、`closestCorners` 碰撞检测、`DragOverlay`、同列内排序

## Capabilities

### New Capabilities
- `kanban-board-management`: 看板和列的 CRUD，默认看板初始化，列保护逻辑（锁定 status_mapping、删除时任务迁移）
- `sprint-board-unification`: Sprint 看板统一复用 KanbanBoard 组件，修复拖拽体验，通过 sprintId 过滤数据

### Modified Capabilities
（无，现有 spec 为空）

## Impact

- **数据库**：`kanban_boards`、`kanban_columns` 表从闲置变为活跃使用；新增种子数据迁移（默认列）
- **后端**：新增 `board/` 包（Entity、Mapper、Service、Controller）；修改 `ProjectService.create()` 初始化默认看板；修改看板列删除逻辑需处理 `tasks.kanban_column_id` 引用
- **前端**：新增看板设置页（列管理 UI）；重构 `ProjectBoardPage` 从 API 加载列；重构 `SprintDetailPage` 看板部分复用 `KanbanBoard` 组件；移除 `constants/index.ts` 中 `KANBAN_COLUMNS` 常量
- **API**：新增 `/api/v1/projects/{pid}/board` 及列管理端点
- **搜索/过滤**：任务状态过滤器改为从列配置动态生成选项；AI 工具 prompt 中的状态描述改为动态读取列配置
- **注意**：`tasks.status` 字段在数据库中是 `VARCHAR(20)`，无 CHECK 约束，天然支持任意值，无需 DDL 变更

## Context

当前看板列定义硬编码在前端两处：`constants/index.ts` 的 `KANBAN_COLUMNS`（6 列，项目看板用）和 `SprintDetailPage.tsx` 的 `STATUS_COLUMNS`（4 列，Sprint 看板用）。数据库 `kanban_boards` 和 `kanban_columns` 表（V2 迁移）已定义完整字段（`color`、`wip_limit`、`status_mapping`、`sort_order`）但从未在 Java 代码中被引用。两套看板 UI 各自独立实现，Sprint 看板的 @dnd-kit 集成不完整（无 `useDroppable`、无 `DragOverlay`、无同列排序）。

## Goals / Non-Goals

**Goals:**
- 看板列从数据库驱动，支持按项目自定义（名称、颜色、WIP 限制、排序）
- 创建项目时自动初始化默认看板和 6 个标准列
- 列的保护逻辑：标准列锁定 `status_mapping`，删除列需任务迁移
- Sprint 看板统一复用 `KanbanBoard`/`KanbanColumn`/`TaskCard` 组件
- 修复 Sprint 看板的拖拽体验

**Non-Goals:**
- 不实现多看板（每个项目只有一个默认看板）

## Decisions

### 1. 每个项目单看板

每个项目只有一个默认看板（`is_default = TRUE`），列通过 `kanban_columns.board_id` 关联。不做多看板切换——当前需求不需要，且数据库模型已支持后续扩展。

### 2. API 路径设计

```
GET    /api/v1/projects/{pid}/board          → 获取当前项目的默认看板（含列）
PUT    /api/v1/projects/{pid}/board/columns   → 批量更新列配置（排序、名称等）
POST   /api/v1/projects/{pid}/board/columns   → 新增列
PATCH  /api/v1/projects/{pid}/board/columns/{id} → 更新单个列
DELETE /api/v1/projects/{pid}/board/columns/{id}?migrateToColumnId={id} → 删除列并迁移任务
```

扁平路由，不嵌套 `/boards/{boardId}/columns`，因为当前只有一个默认看板。

### 3. 列保护规则

| 操作 | 保护规则 |
|------|----------|
| 修改 `status_mapping` | `todo`、`in_progress`、`done` 列的映射**锁定**，返回 400 |
| 删除列 | `todo`、`in_progress`、`done` 列**不可删除**；其他列删除时必须传 `migrateToColumnId` 参数 |
| 新增列 | `status_mapping` 可为任意 VARCHAR(20) 值（不限于预设 6 个），只需不与已有列重复 |
| 排序 | 无限制，所有列可自由调整 `sort_order` |

锁定的 3 个核心状态覆盖了任务的最小生命周期（开始→进行→结束），保证任何看板配置至少支持这个基础流程。锁定的**是 status 值本身**而非列的 id——即使列名被改成"代码审核中"，只要它的 `status_mapping` 是 `in_progress`，就受保护。

### 4. 自定义 status 值

新增列时，`status_mapping` 不再限制为预设 6 个值。团队可以创建真正的自定义状态：

```
示例：新增"阻塞中"列 → status_mapping = "blocked"
```

**数据库约束**：`tasks.status` 字段已是 `VARCHAR(20)`，无 CHECK 约束，天然支持任意值。无需 DDL 变更。

**下游影响**（新增列引入新 status 值后，以下位置需动态适配）：

| 影响点 | 处理方式 |
|--------|----------|
| 任务状态过滤器（前端搜索栏） | 从列配置动态生成下拉选项，不再硬编码 |
| AI 工具 prompt | `TaskTools` 中将状态描述从硬编码改为从列配置动态读取 |
| Sprint 关闭逻辑 | 未完成任务重置为 `todo`——不受影响，因为 `todo` 是受保护列，始终存在 |
| 看板分组 | `kanbanStore.buildColumns()` 按 API 返回的列配置动态分组，天然支持新 status |
| 已有任务兼容 | 历史任务可能持有列配置之外的 status 值，这些任务归入 "未分类" 列展示 |

### 5. 默认列初始化

创建项目时，`ProjectService.create()` 在事务内初始化：

| sort_order | name | status_mapping | color | wip_limit |
|---|---|---|---|---|
| 0 | 待规划 | backlog | `#94a3b8` | 0 |
| 1 | 待处理 | todo | `#e2e8f0` | 0 |
| 2 | 进行中 | in_progress | `#bfdbfe` | 10 |
| 3 | 评审中 | review | `#fef08a` | 5 |
| 4 | 测试中 | testing | `#fce7f3` | 5 |
| 5 | 已完成 | done | `#bbf7d0` | 0 |

颜色选用 Tailwind CSS slate/gray/blue/yellow/pink/green 色系，柔和且区分度高。已有项目通过 Flyway 迁移脚本补充默认看板数据。

### 6. 前端组件复用策略

Sprint 看板不再自己实现一遍，而是给 `KanbanBoard` 加一个 `filter` prop：

```typescript
interface KanbanBoardProps {
  projectId: number;
  filter?: { sprintId?: number };  // 可选过滤条件
}
```

Sprint 详情页传 `filter={{ sprintId }}`，项目看板不传 filter。`kanbanStore.loadTasks()` 根据 filter 调用不同的 API 参数。Sprint 场景下隐藏 `backlog` 和 `testing` 列（通过列配置的 `visible` 判断，或通过 `status_mapping` 值过滤——Sprint 不需要待规划和测试列）。

**更正**：Sprint 内应该保留 `testing`（测试列），因为 Sprint 内的任务确实可能处于测试阶段。之前 Sprint 缺 `testing` 是遗漏。`backlog`（待规划）列在 Sprint 内隐藏是合理的——进 Sprint 的任务已经规划好。

Sprint 看板显示列 = 项目看板所有列 **减去** `backlog` 列（status_mapping 为 backlog 的列）。

### 7. 前端颜色选择器

使用 Ant Design 的 `ColorPicker` 组件，预设 12 种推荐色（`presets`），同时支持自定义色值。颜色值以 hex string 存储在 `color` 字段。

## Risks / Trade-offs

- **已有项目迁移**：已有项目的任务 `kanban_column_id` 可能为 NULL。Flyway 迁移脚本会为已有项目创建默认看板，然后根据 `tasks.status` 值回填 `kanban_column_id`（匹配 `status_mapping`）。如果某个任务 status 值不在任何列的 mapping 中，默认归入 `backlog` 列。
- **前端缓存**：`kanbanStore` 启动时需先加载列配置再加载任务，避免任务出现但列不存在的闪烁。使用 `Promise.all` 或先 `loadBoard` 再 `loadTasks`。
- **列删除的风险**：如果用户强制删除 `todo` 列（虽然锁定 status_mapping 不能改映射，但列本身可以被删除），所有待处理任务必须迁移。`migrateToColumnId` 是必填参数，后端在事务内完成迁移。
- **Sprint 看板的 backlog 隐藏**：通过 status_mapping 过滤而非硬编码。自定义列（status_mapping 非 backlog）会在 Sprint 看板中正常显示。未来如需更细粒度控制（如某自定义列也不在 Sprint 中显示），可扩展 `kanban_columns.visible_in_sprint` 字段。
- **自定义 status 值的搜索兼容**：如果某列被删除，其引入的自定义 status 值可能仍残留在历史任务的 `status` 字段中。这些"孤儿 status"的任务在 `buildColumns` 时归入"未分类"列，用户可手动迁移到正常列。未来可增加后台定时任务清理。

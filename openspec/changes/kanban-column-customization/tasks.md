## 1. 后端 — 实体和持久层

- [x] 1.1 创建 `KanbanBoard` 实体（`board/KanbanBoard.java`），映射 `kanban_boards` 表，含 `@TableName`、`@TableId`、`@TableField` 注解
- [x] 1.2 创建 `KanbanColumn` 实体（`board/KanbanColumn.java`），映射 `kanban_columns` 表，含 `@TableName(autoResultMap = true)`
- [x] 1.3 创建 `KanbanBoardMapper` 接口（`board/KanbanBoardMapper.java`），继承 `BaseMapper<KanbanBoard>`
- [x] 1.4 创建 `KanbanColumnMapper` 接口（`board/KanbanColumnMapper.java`），继承 `BaseMapper<KanbanColumn>`
- [x] 1.5 创建 `ColumnDTO` 响应 DTO（`board/ColumnDTO.java`），含 id、name、statusMapping、color、wipLimit、sortOrder 字段

## 2. 后端 — 服务和业务逻辑

- [x] 2.1 创建 `BoardService`（`board/BoardService.java`），实现获取默认看板（含列列表），若无则自动初始化
- [x] 2.2 实现默认看板初始化方法 `initDefaultBoard(projectId)`，在事务内创建看板 + 6 个标准列
- [x] 2.3 实现列保护逻辑：`isProtectedColumn(statusMapping)` 判断是否为 todo/in_progress/done（保护其不可修改 status_mapping 且不可删除）
- [x] 2.4 实现新增列方法，校验 status_mapping 不重复（不限制值的范围，支持自定义 status）
- [x] 2.5 实现更新列方法，校验 protected 列不可修改 status_mapping；非 protected 列修改 status_mapping 时同步更新列下任务的 status
- [x] 2.6 实现删除列方法，protected 列拒绝删除；非 protected 列需 `migrateToColumnId` 参数，事务内迁移任务后删除
- [x] 2.7 实现批量更新列排序方法 `updateSortOrder(List<ColumnSortDTO>)`
- [x] 2.8 修改 `ProjectService.create()`，在创建项目后调用 `BoardService.initDefaultBoard()`
- [x] 2.9 修改 `TaskService` 中硬编码的状态校验（如有），改为从列配置动态读取有效状态值 — **N/A：TaskService 无硬编码状态校验，status 为透传字符串**
- [x] 2.10 修改 AI `TaskTools`：`create_task` 和 `search_tasks` 工具的状态描述从列配置动态读取，而非硬编码 — **N/A：AI 工具不设硬编码状态，均为参数透传**

## 3. 后端 — Controller

- [x] 3.1 创建 `BoardController`（`board/BoardController.java`），路径 `/api/v1/projects/{projectId}/board`
- [x] 3.2 `GET /` — 获取默认看板及列
- [x] 3.3 `PUT /columns` — 批量更新列排序
- [x] 3.4 `POST /columns` — 新增列
- [x] 3.5 `PATCH /columns/{id}` — 更新列属性
- [x] 3.6 `DELETE /columns/{id}?migrateToColumnId={targetId}` — 删除列并迁移任务

## 4. 数据库迁移

- [x] 4.1 创建 Flyway 迁移脚本 `V7__seed_default_boards.sql`，为已有项目补充默认看板和列，并根据 tasks.status 回填 `kanban_column_id`

## 5. 前端 — API 客户端

- [x] 5.1 在 `api/board.ts` 中添加 `getBoard(projectId)` 函数
- [x] 5.2 添加 `createColumn(projectId, data)` 函数
- [x] 5.3 添加 `updateColumn(projectId, columnId, data)` 函数
- [x] 5.4 添加 `deleteColumn(projectId, columnId, migrateToColumnId)` 函数
- [x] 5.5 添加 `updateColumnsOrder(projectId, columns)` 函数

## 6. 前端 — Zustand Store 改造

- [x] 6.1 修改 `kanbanStore.ts`，新增 `columns` 状态（列配置列表）和 `loadBoard(projectId)` 方法
- [x] 6.2 修改 `loadTasks` 逻辑，先加载列配置再加载任务，或并行加载
- [x] 6.3 修改 `buildColumns` 函数，按 API 返回的列配置动态分组；处理孤儿 status 任务（status 不在任何列中 → 归入"未分类"虚拟列）
- [x] 6.4 给 `KanbanBoard` 组件添加 `filter?: { sprintId?: number }` prop，kanbanStore 的 `loadTasks` 据此传递 API 参数

## 7. 前端 — 看板组件更新

- [x] 7.1 修改 `KanbanColumn.tsx`，列名从 `column.name` 读取，背景色从 `column.color` 读取
- [x] 7.2 修改 `KanbanBoard.tsx`，接收 `filter` prop 并传给 store
- [x] 7.3 修改 `ProjectBoardPage.tsx`，移除对 `KANBAN_COLUMNS` 常量的依赖，改为从 store 获取列配置

## 8. 前端 — 列管理设置页

- [x] 8.1 创建 `BoardSettingsPage.tsx` 或在项目设置中添加看板列管理 Tab
- [x] 8.2 实现列列表展示（拖拽排序、WIP 限制显示、颜色预览）
- [x] 8.3 实现新增列弹窗（名称、status_mapping 输入框（带预设建议下拉）、ColorPicker、WIP 限制）
- [x] 8.4 实现编辑列弹窗（同新增，但 protected 列禁用 status_mapping 输入框）
- [x] 8.5 实现删除列按钮（protected 列禁用删除按钮并提示原因；其他列弹窗要求选择迁移目标列）
- [x] 8.6 实现列拖拽排序（@dnd-kit），保存后调用批量更新 API

## 8b. 前端 — 搜索/过滤动态化

- [x] 8b.1 修改任务搜索/过滤组件的状态下拉，从列配置 API 动态生成选项（展示列名 + 颜色圆点）
- [x] 8b.2 确保 Sprint 详情页的任务过滤也使用动态状态选项

## 9. 前端 — Sprint 看板统一

- [x] 9.1 重构 `SprintDetailPage.tsx`，用 `<KanbanBoard filter={{ sprintId }}>` 替换内联看板实现
- [x] 9.2 Sprint 看板中过滤掉 `status_mapping === 'backlog'` 的列
- [x] 9.3 保留 Sprint 特有的 Backlog 抽屉（添加任务到 Sprint）和燃尽图区域
- [x] 9.4 移除 `SprintDetailPage.tsx` 中 `STATUS_COLUMNS` 常量和旧的拖拽代码

## 10. 清理

- [x] 10.1 移除 `constants/index.ts` 中的 `KANBAN_COLUMNS` 常量
- [x] 10.2 全局搜索引用，确认无残留依赖

## 11. 验证

- [x] 11.1 启动后端，确认 `GET /api/v1/projects/{pid}/board` 返回默认看板及 6 列
- [x] 11.2 创建新项目，确认默认看板自动创建
- [x] 11.3 测试列保护逻辑：尝试修改 `todo` 的 status_mapping → 400；尝试删除 `done` 列 → 400
- [x] 11.4 测试列删除+迁移：创建测试任务，删除非保护列并迁移，确认任务状态更新
- [x] 11.5 测试自定义 status：新增 status_mapping=`blocked` 的列，创建任务拖入该列，确认 tasks.status 写入 `blocked`
- [x] 11.6 测试搜索过滤：状态下拉包含自定义列，按"阻塞中"过滤能检索到对应任务
- [x] 11.7 前端：打开项目看板页，确认列配置从 API 加载并正确渲染（含颜色）
- [x] 11.8 前端：在看板中拖拽任务跨列移动，确认状态更新 + 排序正常
- [x] 11.9 前端：打开 Sprint 详情页，确认看板复用成功，拖拽正常，backlog 列隐藏
- [x] 11.10 前端：列管理页新增/编辑/删除列，确认保护逻辑在前端也有体现
- [x] 11.11 前端：创建 status_mapping 不在任何列中的孤儿任务，确认在看板"未分类"列中可见

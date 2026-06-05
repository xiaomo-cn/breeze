## ADDED Requirements

### Requirement: Sprint 看板统一使用 KanbanBoard 组件

系统 SHALL 让 Sprint 详情页的看板复用项目看板的 `KanbanBoard`/`KanbanColumn`/`TaskCard` 组件，通过 prop 过滤 Sprint 数据。

#### Scenario: Sprint 看板渲染
- **WHEN** 用户打开 Sprint 详情页
- **THEN** 渲染 `KanbanBoard` 组件，传入 `filter={{ sprintId }}` prop，看板只显示该 Sprint 内的任务

#### Scenario: Sprint 看板隐藏 backlog 列
- **WHEN** Sprint 看板加载列配置
- **THEN** `status_mapping` 为 `backlog` 的列不在 Sprint 看板中显示，其余列（todo/in_progress/review/testing/done）正常显示

#### Scenario: Sprint 看板保留测试列
- **WHEN** Sprint 看板渲染
- **THEN** `status_mapping` 为 `testing` 的列正常显示（不再遗漏）

---

### Requirement: Sprint 看板完整拖拽支持

系统 SHALL 让 Sprint 看板支持完整的拖拽体验，与项目看板一致。

#### Scenario: 跨列拖拽任务
- **WHEN** 用户在 Sprint 看板中将任务从"待处理"拖到"进行中"
- **THEN** 任务的状态更新为新列对应的 `status_mapping`，且任务在目标列末尾显示

#### Scenario: 同列内排序
- **WHEN** 用户在同一列内上下拖拽任务
- **THEN** 任务的 `sort_order` 更新，列内任务按新顺序排列

#### Scenario: 拖拽时显示浮动卡片
- **WHEN** 用户开始拖拽任务卡片
- **THEN** 显示一个半透明旋转的浮动卡片预览（DragOverlay），原位置卡片变半透明

#### Scenario: 拖拽到列空白区域
- **WHEN** 用户将任务拖到列的空白区域（列头或列底）
- **THEN** 任务移动到该列的末尾

#### Scenario: 拖拽到另一张卡片上
- **WHEN** 用户将任务拖到另一张任务卡片上
- **THEN** 任务插入到该卡片所在位置

#### Scenario: 拖拽失败回滚
- **WHEN** 拖拽操作的 API 调用失败
- **THEN** 看板回滚到拖拽前的状态

---

### Requirement: Sprint 看板使用后端列配置

系统 SHALL 让 Sprint 看板从后端加载列配置，不再使用硬编码的 `STATUS_COLUMNS` 常量。

#### Scenario: Sprint 看板列颜色
- **WHEN** Sprint 看板加载列配置
- **THEN** 列的颜色来自数据库 `kanban_columns.color` 字段，与项目看板颜色一致

#### Scenario: Sprint 看板列名称
- **WHEN** Sprint 看板加载列配置
- **THEN** 列名称来自数据库 `kanban_columns.name` 字段，与项目看板一致

---

### Requirement: 移除前端硬编码列常量

系统 SHALL 移除 `constants/index.ts` 中的 `KANBAN_COLUMNS` 常量和 `SprintDetailPage.tsx` 中的 `STATUS_COLUMNS` 常量。

#### Scenario: 常量清理
- **WHEN** 看板列改为从 API 加载
- **THEN** 前端不再引用 `KANBAN_COLUMNS` 和 `STATUS_COLUMNS` 常量，相关代码全部替换为 API 数据

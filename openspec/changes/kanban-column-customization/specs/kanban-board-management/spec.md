## ADDED Requirements

### Requirement: 项目创建时自动初始化默认看板

系统 SHALL 在项目创建时自动创建一个默认看板及 6 个标准列，列配置（名称、status_mapping、颜色、排序）遵循预设默认值。

#### Scenario: 新建项目获得默认看板
- **WHEN** 用户创建项目成功
- **THEN** 系统自动创建 `is_default = TRUE` 的看板，含 6 列：待规划(backlog/#94a3b8)、待处理(todo/#e2e8f0)、进行中(in_progress/#bfdbfe)、评审中(review/#fef08a)、测试中(testing/#fce7f3)、已完成(done/#bbf7d0)

#### Scenario: 初始化在事务内完成
- **WHEN** 创建项目或初始化默认列任一步骤失败
- **THEN** 整个创建过程回滚，不产生孤立的项目记录或看板记录

---

### Requirement: 获取项目看板配置

系统 SHALL 提供 API 返回项目默认看板及其所有列配置。

#### Scenario: 获取默认看板
- **WHEN** 客户端请求 `GET /api/v1/projects/{pid}/board`
- **THEN** 返回看板信息及其所有列，按 `sort_order` 升序排列，包含 id、name、status_mapping、color、wip_limit、sort_order 字段

#### Scenario: 项目无默认看板时自动初始化
- **WHEN** 请求看板但项目尚无默认看板（兼容历史项目）
- **THEN** 系统自动初始化默认看板和列后返回

---

### Requirement: 前端从后端加载看板列

系统 SHALL 支持前端在加载看板页面时从 API 获取列配置，取代前端硬编码常量。

#### Scenario: 项目看板页加载
- **WHEN** 用户打开项目看板页面
- **THEN** 前端先请求看板配置 API 获取列定义，再按列加载任务数据

#### Scenario: 列配置变更后看板自动反映
- **WHEN** 管理员修改了列配置（如名称、颜色）
- **THEN** 下次加载看板页面时看到更新后的列

---

### Requirement: 更新列属性

系统 SHALL 允许修改列的显示属性（名称、颜色、WIP 限制、排序），但对核心列的 `status_mapping` 实施保护。

#### Scenario: 修改列的显示属性
- **WHEN** 管理员请求 `PATCH /api/v1/projects/{pid}/board/columns/{id}` 修改列名、颜色或 WIP 限制
- **THEN** 系统更新对应字段并返回更新后的列

#### Scenario: 修改受保护列的 status_mapping 被拒绝
- **WHEN** 管理员尝试修改 `status_mapping` 为 `todo`、`in_progress` 或 `done` 的列的映射值
- **THEN** 系统返回 400 错误，提示该列的 status_mapping 不可修改

#### Scenario: 修改非保护列的 status_mapping 成功
- **WHEN** 管理员修改 `backlog`、`review` 或 `testing` 列的 `status_mapping`
- **THEN** 系统更新映射，同时该列下所有任务的 `kanban_column_id` 保持不变（仍属于该列），`status` 更新为新映射值

---

### Requirement: 新增自定义列

系统 SHALL 允许项目管理员在看板中新增自定义列，`status_mapping` 可为任意不重复的值。

#### Scenario: 成功新增列（预设状态值）
- **WHEN** 管理员提交新列信息（name、status_mapping=review、color、wip_limit、sort_order）
- **THEN** 系统创建新列，sort_order 冲突时后续列自动后移

#### Scenario: 成功新增列（自定义 status 值）
- **WHEN** 管理员提交新列，status_mapping 为自定义值（如 `blocked`、`design`、`rejected`）
- **THEN** 系统创建新列，tasks.status 字段无需 DDL 变更即可接受该值（VARCHAR(20) 天然支持）

#### Scenario: status_mapping 重复时新增被拒绝
- **WHEN** 管理员新增列时，指定的 `status_mapping` 已被其他列占用
- **THEN** 系统返回 400 错误，提示该状态映射已被占用

---

### Requirement: 删除列并迁移任务

系统 SHALL 允许删除非保护列（status_mapping 非 todo/in_progress/done），但列下的任务必须迁移到指定目标列。受保护列不可删除。

#### Scenario: 删除列成功
- **WHEN** 管理员请求 `DELETE /api/v1/projects/{pid}/board/columns/{id}?migrateToColumnId={targetId}`，且该列 status_mapping 非 todo/in_progress/done
- **THEN** 该列下所有任务的 `kanban_column_id` 和 `status` 更新为目标列的值，然后删除该列

#### Scenario: 删除受保护列被拒绝
- **WHEN** 管理员尝试删除 status_mapping 为 `todo`、`in_progress` 或 `done` 的列
- **THEN** 系统返回 400 错误，提示该列不可删除

#### Scenario: 删除列时缺少迁移目标列
- **WHEN** 管理员请求删除列但未提供 `migrateToColumnId` 参数
- **THEN** 系统返回 400 错误，提示必须指定迁移目标列

#### Scenario: 删除列时列下有 0 个任务
- **WHEN** 管理员删除一个空列
- **THEN** 系统直接删除该列，无需关心迁移（迁移参数仍提交，只是无任务需迁移）

#### Scenario: 事务内完成迁移和删除
- **WHEN** 迁移任务或删除列任一操作失败
- **THEN** 整个操作回滚，列和任务保持原状

---

### Requirement: 批量更新列排序

系统 SHALL 支持一次性更新所有列的 `sort_order`。

#### Scenario: 拖拽列调整顺序
- **WHEN** 管理员提交所有列的排序信息
- **THEN** 系统批量更新各列的 `sort_order`

---

### Requirement: 列颜色配置

系统 SHALL 支持为每列设置独立颜色，通过前端颜色选择器选取。

#### Scenario: 设置列颜色
- **WHEN** 管理员通过 ColorPicker 为列选择颜色
- **THEN** 颜色值以 hex string 格式（如 `#bfdbfe`）存入数据库并在看板上渲染

#### Scenario: 默认颜色
- **WHEN** 新建列时未指定颜色
- **THEN** 系统使用默认值 `#808080`

---

### Requirement: 任务状态过滤器动态生成

系统 SHALL 让任务搜索/过滤的状态下拉选项从列配置动态生成，而非硬编码。

#### Scenario: 搜索栏状态下拉
- **WHEN** 用户打开任务搜索过滤的状态下拉
- **THEN** 下拉选项来自项目看板列配置，展示列名和颜色标识，选项值对应 `status_mapping`

#### Scenario: 新增自定义列后下拉自动更新
- **WHEN** 管理员新增了一个 status_mapping=`blocked` 的列
- **THEN** 任务过滤状态下拉自动出现"阻塞中"选项

---

### Requirement: AI 工具 prompt 动态读取列配置

系统 SHALL 让 AI 创建任务和搜索任务时，可用状态信息从列配置动态读取。

#### Scenario: AI 创建任务时的状态描述
- **WHEN** AI Agent 收到创建任务请求
- **THEN** 系统 prompt 中描述的任务状态选项来自项目看板列配置，而非硬编码的 6 个状态

#### Scenario: AI 搜索任务时的状态过滤
- **WHEN** AI Agent 执行 `search_tasks` 工具
- **THEN** 状态过滤参数接受列配置中存在的任意 `status_mapping` 值

---

### Requirement: 未分类任务处理

系统 SHALL 对持有列配置之外 status 值的任务提供"未分类"展示，防止这些任务在看板上消失。

#### Scenario: 孤儿 status 任务归入未分类列
- **WHEN** 某任务的 `status` 值不匹配任何列的 `status_mapping`（如列被删除后的遗留任务）
- **THEN** 该任务在看板最左侧的"未分类"虚拟列中显示，用户可将其拖到正常列进行重新分类

#### Scenario: 未分类列为空时不显示
- **WHEN** 所有任务的 status 都能匹配到现有列
- **THEN** "未分类"列不在看板中显示

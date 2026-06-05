# Task

## Create Task

项目成员可以手动创建任务。

### Scenario: 创建任务

- **WHEN** 用户在项目中提供 title、type、priority
- **THEN** 系统创建任务，自动生成 key（{PROJECT_KEY}-{自增数字}），默认状态为 todo

### Scenario: 缺少必填字段

- **WHEN** 用户未提供 title
- **THEN** 系统返回 400 错误

## List Tasks

用户可以查看项目下的任务列表。

### Scenario: 按状态过滤

- **WHEN** 用户请求任务列表并指定 status=todo
- **THEN** 系统返回该项目下所有状态为 todo 的任务

## Update Task Status (Kanban Drag)

用户可以通过拖拽改变任务状态。

### Scenario: 拖拽任务到新列

- **WHEN** 用户将任务从 "Backlog" 列拖到 "In Progress" 列
- **THEN** 系统更新任务状态为 in_progress，更新 sort_order

## AI Create Task

AI 可以通过对话自然语言创建任务（Spring AI Tool Calling）。

### Scenario: AI 创建任务成功

- **GIVEN** 项目 pm-ai 中存在成员"张三"
- **WHEN** 用户在 AI 对话框输入"帮我创建一个高优的登录页面修复 bug，分配给张三，周五前完成"
- **THEN** AI 调用 create_task tool，系统创建任务并返回任务 key
- **AND** 看板上出现新任务卡片

### Scenario: AI 搜索任务

- **WHEN** 用户在 AI 对话框输入"项目中所有高优 bug 有哪些？"
- **THEN** AI 调用 search_tasks tool，返回匹配的任务列表

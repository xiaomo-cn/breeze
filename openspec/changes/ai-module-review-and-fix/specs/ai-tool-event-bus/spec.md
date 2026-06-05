## ADDED Requirements

### Requirement: 工具执行时发布开始事件
当 AI Agent 决定调用任何 @Tool 方法时，系统 SHALL 在工具开始执行前向当前对话的 SSE 流发布 `tool_start` 事件。

#### Scenario: 创建任务时发布开始事件
- **WHEN** AI 调用 `create_task` 工具
- **THEN** SSE 流中收到事件类型为 `tool_start`，数据包含 `toolName: "create_task"`、`message: "正在创建任务: <标题>"`

### Requirement: 工具完成时发布结束事件
当 @Tool 方法执行完成（无论成功或失败）时，系统 SHALL 向当前对话的 SSE 流发布 `tool_end` 事件。

#### Scenario: 搜索任务成功完成
- **WHEN** `search_tasks` 执行完成且找到结果
- **THEN** SSE 流中收到事件类型为 `tool_end`，数据包含 `toolName: "search_tasks"`、`message: "找到 N 个任务"`

#### Scenario: 工具执行失败
- **WHEN** 任何 @Tool 方法执行抛出异常
- **THEN** SSE 流中收到事件类型为 `tool_end`，message 以 "❌" 开头包含错误描述

### Requirement: 对话级事件隔离
每个对话的工具事件 SHALL 仅推送到该对话的 SSE 连接，不同对话之间的事件互不干扰。

#### Scenario: 两个用户同时使用 AI 对话
- **WHEN** 用户 A 的对话触发 `create_task` 且用户 B 的对话触发 `search_tasks`
- **THEN** 用户 A 的 SSE 流仅收到 `create_task` 事件，用户 B 的 SSE 流仅收到 `search_tasks` 事件

### Requirement: 前端展示工具状态卡片
前端聊天面板 SHALL 在助理消息上方展示工具调用的运行状态，包括运行中、成功、失败三种状态。

#### Scenario: 展示运行中的工具
- **WHEN** 前端收到 `tool_start` 事件
- **THEN** 助理消息上方显示带 "🔧" 图标和黄色背景的工具状态卡片，文字为 `message` 内容，带闪烁动画

#### Scenario: 工具完成更新状态
- **WHEN** 前端收到同一工具的 `tool_end` 事件
- **THEN** 对应卡片图标变为 "✅"（成功）或 "❌"（失败），背景变为绿色或红色

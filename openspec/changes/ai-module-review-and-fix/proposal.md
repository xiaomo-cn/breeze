## Why

AI 模块在安全审计中发现了多个安全漏洞（无认证端点可执行 SQL、关键字过滤可绕过）、数据完整性风险（竞态条件导致跨对话串扰、流中断产生孤儿数据）以及性能问题（N+1 查询、LIKE 全表扫描）。同时，AI 工具调用对用户完全不透明——用户在对话中不知道 AI 正在操作什么。

## What Changes

### 安全修复（P0）
- `nl-query` / `nl-query/execute` 端点添加用户认证 + 项目成员权限校验
- SQL 关键字过滤统一大写检查，防止大小写混合绕过；注释去除后判断 `startsWith("SELECT")`
- 工具执行记录按 `user_id` 精确关联，修复并发场景下的跨对话串扰

### 数据完整性（P1）
- SSE 流添加 `doOnCancel` / `doFinally` 处理，客户端断开时保存中断消息
- 删除对话时连带清理 `ai_tool_executions` 孤儿记录
- 移除 `ai_messages` 表中实体未映射的冗余列（`tool_calls`/`tool_call_id`/`tool_name`）

### 功能修复（P2）
- `TaskTools` 统一返回 `String` 类型（与 `WriteTools`/`ReadTools` 一致），异常时返回友好错误消息
- `create_task` 增加 `type`/`priority` 枚举校验、指派人未找到明确报错、日期格式校验
- AI_USER_ID 常量化

### 性能优化
- `searchTasks` 切换为 `fulltextSearch`（ILIKE + 排序 + LIMIT）
- `listMembers` 修复 N+1 查询 → `selectByIds` 批量查询
- 聊天历史添加分页支持（`page`/`size` 参数）
- 对话列表增加 `messageCount` / `lastMessage` 统计字段

### 新功能：工具调用状态实时展示
- 后端 `ToolEventPublisher` 响应式事件总线，@Tool 方法执行时发布 `tool_start`/`tool_end` 事件
- SSE 流合并文本块和工具事件，前端解析结构化事件类型
- 前端聊天界面展示 "🔧 正在创建任务..." → "✅ 任务已创建" 工具状态卡片

## Capabilities

### New Capabilities
- `ai-tool-event-bus`: 工具调用事件的响应式发布/订阅，合并到 SSE 流推送给前端
- `ai-security-hardening`: AI 端点的认证加固、SQL 注入防护、竞态条件修复

### Modified Capabilities
<!-- 无现有 spec 变更 -->

## Impact

- 后端：`AiController`、`AiAgentService`、`TaskTools`、`WriteTools`、`ReadTools`、`NlQueryService`、`AiToolExecution`
- 新增文件：`AiRequestContext.java`、`ToolEventPublisher.java`、`V10__tool_exec_user_id.sql`、`V11__cleanup_message_columns.sql`
- 前端：`ai.ts`（SSE 解析）、`AiChatPanel.tsx`（工具状态卡片）
- **无破坏性变更**：所有 API 兼容，新增可选参数

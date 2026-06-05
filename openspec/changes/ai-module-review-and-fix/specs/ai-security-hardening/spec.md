## ADDED Requirements

### Requirement: NL-Query 端点需要认证和项目权限
`/api/v1/ai/nl-query` 和 `/api/v1/ai/nl-query/execute` 端点 SHALL 通过 JWT 认证获取当前用户，并校验用户是否为目标项目的成员。

#### Scenario: 未认证请求被拒绝
- **WHEN** 请求不含有效 JWT token
- **THEN** 系统返回 401 Unauthorized

#### Scenario: 非项目成员被拒绝
- **WHEN** 已认证用户请求非自己所属项目的 nl-query
- **THEN** 系统返回 403 Forbidden，消息为 "无权访问该项目"

### Requirement: SQL 关键字过滤防止大小写绕过
SQL 安全检查 SHALL 对输入 SQL 做统一大写转换后再进行关键字匹配，并在判断 `SELECT` 前缀前去除所有 SQL 注释。

#### Scenario: 大小写混合关键字被拦截
- **WHEN** 用户提交包含 `"InSeRt"` 的 SQL
- **THEN** 系统抛出 `IllegalArgumentException`，消息包含 "禁止的 SQL 操作: INSERT"

#### Scenario: 注释绕过被拦截
- **WHEN** 用户提交 `"/*!50000 Select */ * FROM users"`
- **THEN** 系统抛出 `IllegalArgumentException`，消息包含 "仅允许 SELECT 查询"

### Requirement: 工具执行记录按用户隔离
工具执行记录的关联 SHALL 使用 `user_id` + `conversation_id IS NULL` + 时间窗口的组合条件，防止并发场景下的跨对话串扰。

#### Scenario: 同时间多用户对话不串扰
- **WHEN** 用户 A 和用户 B 同时与 AI 对话，各自触发工具调用
- **THEN** 用户 A 的工具执行记录仅关联到用户 A 的对话，用户 B 的记录仅关联到用户 B 的对话

### Requirement: 流中断时保存错误消息
当 SSE 流因客户端断开或错误而终止时，系统 SHALL 在数据库中保存错误标记消息，避免孤儿用户消息。

#### Scenario: 客户端关闭浏览器时保存中断消息
- **WHEN** 客户端在 AI 流式回复过程中断开连接
- **THEN** `ai_messages` 表中保存一条 role=assistant 的消息，内容包含 "[对话已中断]"

### Requirement: 删除对话时清理工具执行记录
删除对话时 SHALL 同时删除关联的 `ai_messages` 和 `ai_tool_executions` 记录。

#### Scenario: 删除对话
- **WHEN** 用户调用 DELETE /api/v1/ai/conversations/{id}
- **THEN** 该对话的 ai_tool_executions、ai_messages、ai_conversations 记录全部被删除

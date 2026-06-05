## Context

AI 模块当前状态：Spring AI (DeepSeek V4 Pro) + `@Tool` 注解驱动，11 个工具分布在 TaskTools/WriteTools/ReadTools 三个类中。SSE 流式返回文本块。

经过全面审计发现：
- 安全层：nl-query 端点依赖 Spring Security 的全局 `/api/v1/**` 认证，但缺少用户身份获取和项目成员校验；SQL 关键字过滤只检查了全大写/全小写形式
- 数据层：工具执行记录的归属关联通过 10 秒时间窗口 + conversationId IS NULL 实现，无用户维度，并发时串扰
- 体验层：AI 工具调用对用户不可见，只知道最终结果

## Goals / Non-Goals

**Goals:**
- 消除 P0 安全漏洞：端点认证 + SQL 注入防护 + 竞态条件
- 消除 P1 数据完整性风险：流中断处理 + 孤儿记录清理 + schema 对齐
- 修复 P2 功能缺陷：Tool 返回类型统一 + 参数校验
- 优化性能：全文搜索 + N+1 查询修复 + 分页
- 实现工具调用状态实时可见

**Non-Goals:**
- 对话摘要压缩（留待 Batch 6）
- 断线重连（留待 Batch 7）
- 工具调用确认机制（留待 Batch 7）
- 多模态输入（留待 Batch 8）

## Decisions

### 1. AiRequestContext — ThreadLocal 传递用户上下文
**选择**: 使用 ThreadLocal 在 `AiAgentService` 设置 userId/conversationId，供 @Tool 方法获取。
**原因**: @Tool 方法由 Spring AI 框架反射调用，无法直接访问 HTTP 请求。ThreadLocal 是 Spring 生态中传递请求级上下文的惯用模式（类比 `RequestContextHolder`、`SecurityContextHolder`）。
**备选**: 可通过 @ToolParam 让 LLM 传 userId，但 LLM 可能传错误值，不可靠。

### 2. ToolEventPublisher — Sinks.Many 响应式事件总线
**选择**: 使用 Reactor `Sinks.Many.multicast().onBackpressureBuffer()` 实现工具事件的发布/订阅。每个对话创建独立 Sink。
**原因**: 
- @Tool 方法同步执行，Sinks.Many 支持多线程发布（线程安全）
- `Flux.merge(chatContent, toolEvents)` 将文本块和工具事件合并为一个 SSE 流
- 后端压力控制通过 `onBackpressureBuffer` 处理
**备选**: 使用 Spring ApplicationEvent + SseEmitter，但需要异步监听器，增加复杂度。

### 3. TaskTools 返回 String vs 实体对象
**选择**: 统一所有 Tool 返回 `String`（人类可读文本），而非 `Task`/`List<Task>` 等实体。
**原因**: 
- LLM 收到的实体 JSON 包含大量冗余字段（isDeleted、createdAt 等），浪费 token
- WriteTools/ReadTools 已使用此模式，TaskTools 需要对齐
- 异常时返回 "❌ 错误描述" 而非让 Spring AI 框架抛出通用错误

### 4. SQL 过滤 — 先 uppercase 再检查
**选择**: `sql.toUpperCase()` 后再与 `FORBIDDEN_KEYWORDS` 集比对；去除 SQL 注释后再判断 `startsWith("SELECT")`。
**原因**: 大小写混合攻击（`"InSeRt"`）和 MySQL 条件注释绕过（`/*!50000 Select */`）是最常见的 SQL 注入绕过手法。当前方案成本最低且覆盖这两种攻击向量。
**备选**: 使用 JSqlParser 做 AST 级 SQL 白名单校验（更安全但引入新依赖，留待后续增强）。

## Risks / Trade-offs

- **ThreadLocal 泄漏风险**: 如果 Flux 异常终止且 doFinally 未执行，ThreadLocal 不会清理。已通过 `doFinally` + `doOnCancel` 双重保障。
- **Sinks.Many 内存泄漏**: 如果客户端断开但 session 未被清理，Sink 残留在 Map 中。通过 `doOnCancel`/`doOnTerminate` 触发 `removeSession`，以及 `ToolEventPublisher.completeSession` 在 doFinally 中调用。
- **Flux.merge 顺序**: 工具事件可能比文本块先到达前端（工具调用发生在 LLM 生成文本之前）。前端已正确处理：工具状态卡片在消息气泡上方独立展示。
- **Flyway V11 删列不可逆**: 删除 `tool_calls`/`tool_call_id`/`tool_name` 三列后，如果需要恢复只能从备份还原。当前业务逻辑完全不使用这三列，风险可控。

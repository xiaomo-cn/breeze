## 1. P0 安全修复

- [x] 1.1 `nlQuery` / `executeNlQuery` 添加 Authentication 参数 + 项目成员权限校验 `isProjectMember()`
- [x] 1.2 SQL 关键字过滤统一 `toUpperCase()` 检查；去除注释后判断 `startsWith("SELECT")`；移除冗余小写关键词
- [x] 1.3 创建 `AiRequestContext`（ThreadLocal 传递 userId/conversationId）；`AiToolExecution` 新增 `userId` 字段；V10 迁移添加 `user_id` 列+索引
- [x] 1.4 `linkRecentToolExecutions` 按 `userId` 过滤 + MyBatis-Plus 条件精确匹配；三个工具类 `logToolExecution` 记录 `userId`

## 2. P1 数据完整性

- [x] 2.1 `AiAgentService.streamChat` 添加 `doOnCancel` 保存中断消息；`doFinally` 清理 `AiRequestContext`
- [x] 2.2 `deleteConversation` 同步删除 `ai_tool_executions` 记录
- [x] 2.3 V11 迁移删除 `ai_messages` 表中实体未映射的 `tool_calls`/`tool_call_id`/`tool_name` 三列

## 3. P2 功能修复

- [x] 3.1 `TaskTools` 重写：`createTask`/`searchTasks`/`listMembers` 统一返回 `String`，异常时返回 "❌" 友好错误消息
- [x] 3.2 `create_task` 增加 `type`/`priority` 枚举校验（`VALID_TYPES`/`VALID_PRIORITIES`）；指派人未找到返回明确错误；日期格式校验
- [x] 3.3 `WriteTools` 和 `ReadTools` 的 `logToolExecution` 统一记录 `userId`

## 4. 性能优化

- [x] 4.1 `searchTasks` 有关键词时调用 `taskMapper.fulltextSearch()`（ILIKE + 排序 + LIMIT 50）
- [x] 4.2 `listMembers` 改为先查 memberId 列表再 `selectByIds` 批量查询（2 次 SQL 替代 N+1）
- [x] 4.3 `getMessages` 添加 `page`/`size` 分页参数，默认每页 50 条
- [x] 4.4 `listConversations` 返回增加 `messageCount` / `lastMessage` 字段；前端 `Conversation` 接口补充

## 5. 工具调用状态实时展示

- [x] 5.1 创建 `ToolEventPublisher`（`Sinks.Many` 响应式事件总线，按 conversationId 隔离）
- [x] 5.2 `TaskTools`/`WriteTools`/`ReadTools` 各 @Tool 方法注入 `ToolEventPublisher`，执行前后发布 `publishStart`/`publishEnd`
- [x] 5.3 `AiAgentService` 创建 `toolEvents` Flux + `Flux.merge(chatContent, toolEvents)` 合并 SSE 流
- [x] 5.4 前端 `ai.ts`：`streamChat` 支持解析 `event: tool_start`/`event: tool_end` SSE 事件类型，新增 `StreamCallbacks` 接口
- [x] 5.5 前端 `AiChatPanel`：`ChatMessage` 增加 `toolCalls` 字段；渲染工具状态卡片（🔧运行中/✅成功/❌失败）

## 6. 对话摘要与智能压缩

- [x] 6.1 实现滑动窗口摘要：消息超过 20 条时，`@Async` 后台调用 AI 生成早期消息摘要，存储到 `contextSnapshot`
- [x] 6.2 引入 JTokkit (CL100K_BASE) 替换字符级 TokenCounter 估算
- [x] 6.3 摘要持久化：`summarizeConversationAsync` 写入 `contextSnapshot`（summary + summarizedUpToId），下次加载时注入 system prompt 并跳过已摘要消息

## 7. 前端体验增强

- [x] 7.1 SSE 断线重连：`streamChat` 支持指数退避重试（2s→4s→8s，最多3次）+ `AbortSignal` 支持 + 连接状态指示器
- [x] 7.2 工具调用确认机制：写操作（create_task）拦截 → `tool_confirmation` SSE 事件 → 前端确认/取消按钮 → `POST /confirm-tool`/`POST /reject-tool`
- [x] 7.3 `AiChatPanel` 对话侧边栏显示 `messageCount` 和 `lastMessage` 预览

## 8. 远期功能（未开始）

- [ ] 8.1 多模态输入：支持粘贴截图，AI 识别看板/错误信息
- [ ] 8.2 AI 主动通知：结合 `RiskAssessmentService` + 定时任务 + WebSocket 推送延期/风险预警
- [ ] 8.3 SQL 校验升级：引入 JSqlParser 做 AST 级白名单校验，替代当前黑名单子串匹配

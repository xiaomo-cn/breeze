# Phase 3: Tasks

## Comments

- [x] 实现评论 CRUD API（POST/GET/PUT/DELETE /tasks/{id}/comments）
- [x] 支持嵌套回复（parent_id + 树形展示）
- [x] Markdown 编辑 + 预览（@tiptap/react）
- [x] @ 提及检测 → 触发通知
- [x] 评论列表分页 + SSE 实时推送新评论

## File Attachments

- [x] 实现 FileStorageService 接口
- [x] 实现 LocalFileStorageService（开发环境本地存储，生产环境可替换 S3）
- [x] POST /tasks/{taskId}/attachments → Multipart 上传
- [x] GET /attachments/{id}/download → 文件下载
- [x] DELETE /attachments/{id} → 删除文件
- [x] 前端：文件上传 + 列表 + 下载 + 删除（AttachmentList 组件，集成到 TaskDetailDrawer）

## Notifications

- [x] 实现通知生成（CommentService 中的 COMMENT_ADDED、MENTIONED；其他类型按需在各业务 Service 中补充）
- [x] GET /api/v1/notifications（分页列表）
- [x] GET /api/v1/notifications/unread-count
- [x] PATCH /api/v1/notifications/{id}/read
- [x] PATCH /api/v1/notifications/read-all
- [x] SSE 推送：通过 `GET /api/v1/events/stream` 的 `notification` 事件
- [x] 前端：通知 bell 图标 + 未读红点 + 下拉列表

## Sprint

- [x] Sprint CRUD API（/projects/{pid}/sprints）
- [x] POST /sprints/{id}/start → 状态从 planning → active
- [x] POST /sprints/{id}/close → 状态从 active → closed
- [x] 关闭时未完成任务自动移到 Backlog
- [x] GET /sprints/{id}/burndown → 燃尽图数据
- [x] 前端：Sprint 页面（列表 + 创建/启动/关闭）
- [x] 前端：燃尽图（Recharts 折线图：理想线 vs 实际线）

## Activity Log

- [x] 实现操作审计记录（Service 内联记录 + ActivityLogger 组件）
- [x] GET /api/v1/projects/{pid}/activity
- [x] 前端：Dashboard 最近活动 Timeline

## Real-time Task Updates (SSE)

- [x] 后端：TaskService CUD 操作后通过 SseEmitterRegistry 广播 `task_updated` / `task_deleted` 事件
- [x] 前端：`useRealtimeEvents` 订阅 `task_updated` / `task_deleted`，调 `diffAndMerge` 增量更新看板
- [x] 前端：ProjectBoardPage 移除 `usePolling` 轮询，仅保留首次 `loadTasks`
- [ ] 验证：两个浏览器窗口，A 创建/拖拽任务 → B 看板实时更新（无需 10s 等待）

## Verification

- [ ] 多用户协作：评论 + 回复 + @ 通知
- [ ] 文件上传 → MinIO 存储 → 下载
- [ ] Sprint 创建 → 添加任务 → 启动 → 关闭 → 燃尽图
- [ ] 通知实时推送（打开两个浏览器，A 给 B 分配任务 → B 收到通知）
- [ ] 任务实时更新（打开两个浏览器，A 拖拽任务 → B 看板实时同步）

# Phase 3: Design

## File Upload Flow (Pre-signed URL)

```
前端选择文件
    ↓
POST /api/v1/projects/{pid}/tasks/{tid}/attachments/prepare
    { fileName, fileSize, contentType }
    ↓
后端校验 (≤50MB)
    → 生成 objectKey: projects/{pid}/tasks/{tid}/{uuid}_{fileName}
    → 插入 DB: status=pending
    → generatePresignedUploadUrl(objectKey, 5min)
    ↓
返回 { attachmentId, uploadUrl, objectKey }
    ↓
前端直接 PUT 文件到 uploadUrl (绕过 Spring Boot)
    ↓
POST /attachments/{id}/confirm
    ↓
后端标记 status=uploaded
```

## Notification Types (11)

| 类型 | 描述 |
|------|------|
| task_assigned | 任务分配给你 |
| task_status_changed | 你关注的任务状态变更 |
| task_commented | 你的任务有新评论 |
| task_mentioned | 在评论中被 @ |
| task_due_soon | 任务即将到期（24h） |
| task_overdue | 任务已逾期 |
| sprint_started | Sprint 已启动 |
| sprint_closed | Sprint 已关闭 |
| project_invited | 被邀请加入项目 |
| comment_replied | 你的评论被回复 |
| ai_suggestion | AI 提出建议 |

## Sprint & Burndown

```
Sprint 生命周期:
  planning → active → closed

燃尽图数据:
  GET /api/v1/projects/{pid}/sprints/{sid}/burndown
  返回: [{ date, idealRemaining, actualRemaining }]
```

理想线 = Sprint 总 story points / Sprint 天数
实际线 = 每天结束时剩余未完成的 story points

# Phase 3: Collaboration

## Why

项目管理的核心是多人协作。Phase 3 添加任务评论（Markdown）、文件附件上传、通知系统（站内 + SSE 实时推送）、Sprint 规划管理和燃尽图。从"个人任务管理"升级为"团队协作工具"。

## What

- 任务评论（嵌套回复 + Markdown 编辑）
- 文件附件上传（预签名 URL 直传 MinIO/OSS）
- 通知系统（11 种通知类型，站内 + SSE 实时推送）
- 文件存储 S3 抽象（MinIO 开发，阿里云 OSS 生产）
- Sprint 模块（规划、启动、关闭、燃尽图）
- 活动日志（操作审计）

## Impact

- 依赖：Phase 1 + Phase 2
- 新增：评论、附件、通知、Sprint 模块
- 新增：FileStorageService 接口 + S3 实现

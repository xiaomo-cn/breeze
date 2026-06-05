# Phase 4: Tasks

## Reports

- [ ] 日报生成 API：今日完成任务、进行中任务、阻塞任务
- [ ] 周报生成 API：本周统计（新增/完成/剩余）、Burndown 趋势
- [ ] Sprint 报告：Sprint 完成率、个人贡献统计
- [ ] GET /reports/export?type=daily|weekly|sprint&format=pdf|csv
- [ ] PDF 导出（Thymeleaf 模板 → Flying Saucer 渲染）
- [ ] CSV 导出（OpenCSV）
- [ ] 前端：报表中心页面（报表类型选择 + 时间范围 + 图表展示）
- [ ] Recharts 图表：柱状图（每日完成数）、饼图（任务分布）、折线图（趋势）

## Gantt Chart

- [ ] GET /api/v1/projects/{pid}/gantt → 返回甘特图数据
- [ ] 前端：甘特图页面（frappe-gantt 集成）
- [ ] 支持拖拽调整任务起止日期
- [ ] 支持拖拽调整任务依赖连线
- [ ] 按 Sprint / 按指派人分组视图切换

## Task Dependencies

- [ ] 实现依赖关系 CRUD：POST/GET/DELETE /tasks/{id}/dependencies
- [ ] 三种依赖类型：blocks（阻塞）、relates_to（关联）、duplicates（重复）
- [ ] 循环依赖检测（阻断 blocks 类型形成循环）
- [ ] 前端：TaskDetailModal 中显示依赖关系图
- [ ] 阻断任务未完成时显示警告

## Verification

- [ ] 日报/周报/Sprint 报告生成正确
- [ ] PDF 导出格式正常、中文不乱码
- [ ] CSV 导出可用 Excel 打开
- [ ] 甘特图显示任务时间线和依赖箭头
- [ ] 拖拽调整依赖: task A 日期后移 → task B 自动后移
- [ ] 循环依赖检测: blocks 循环 → 返回错误提示

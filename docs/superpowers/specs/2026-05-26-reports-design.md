# Phase 4-1: 报表模块设计

## 范围

实现项目报表中心，支持日报/周报/Sprint 报告的数据聚合与图表展示，以及 PDF/CSV 导出。

甘特图和任务依赖关系延后到报表完成后实现。

## 后端

### 新增类

| 类 | 包 | 职责 |
|---|---|---|
| `ReportController` | `cn.xiaomo.breeze.report` | 3 个报表端点 + 导出端点 |
| `ReportService` | `cn.xiaomo.breeze.report` | 聚合查询 TaskMapper + SprintMapper，组装报表 DTO |
| `PdfExportService` | `cn.xiaomo.breeze.report` | Thymeleaf 模板 → Flying Saucer → PDF byte[] |
| `ReportDTO` / `DailyReportDTO` / `WeeklyReportDTO` / `SprintReportDTO` | `cn.xiaomo.breeze.report.dto` | 报表数据结构 |

### API

```
GET /api/v1/projects/{pid}/reports/daily?date=2026-05-26
GET /api/v1/projects/{pid}/reports/weekly?start=2026-05-19&end=2026-05-26
GET /api/v1/projects/{pid}/reports/sprint/{sprintId}
GET /api/v1/projects/{pid}/reports/export?type=daily&format=pdf&date=...
```

### 数据聚合逻辑

- **日报**：当日完成任务列表、进行中任务、阻塞任务、当日统计（新增/完成数）
- **周报**：本周新增/完成/剩余趋势（每天数据点，供折线图）、任务分布（按状态饼图）、成员贡献
- **Sprint 报告**：复用 SprintService.burndown() 数据 + 完成率 + 个人完成统计

### PDF 导出

- 依赖：`org.xhtmlrenderer:flying-saucer-pdf-itext5:9.1.22`
- 模板：`resources/templates/report-daily.html`、`report-weekly.html`、`report-sprint.html`
- 中文字体：思源黑体放入 `resources/fonts/SourceHanSansSC-Regular.ttf`，Flying Saucer 配置字体路径

### CSV 导出

手动拼接，`text/csv; charset=UTF-8`，BOM 头确保 Excel 正确识别中文。

## 前端

### 新增文件

| 文件 | 职责 |
|------|------|
| `pages/ReportPage.tsx` | 报表中心主页面 |
| `components/report/ReportTypeSelector.tsx` | 日报/周报/Sprint 切换 |
| `components/report/DailyReport.tsx` | 日报展示（统计卡片 + 任务列表） |
| `components/report/WeeklyReport.tsx` | 周报展示（趋势折线图 + 饼图 + 表格） |
| `components/report/SprintReport.tsx` | Sprint 报告（复用 BurndownChart + 完成率） |
| `api/reports.ts` | 报表 API 客户端 |
| `types/report.ts` | TypeScript 类型 |

### 路由

`/projects/:id/reports` → ReportPage，Sidebar 添加"报表"入口。

### 图表

使用已有的 Recharts 库：
- `LineChart` 展示周趋势和燃尽图
- `PieChart` 展示任务分布
- `BarChart` 展示成员贡献

### 导出

前端调用导出 API 后触发浏览器下载（`Content-Disposition: attachment`）。

## 不包含

- AI 生成报告文字（Phase 6）
- 甘特图（Phase 4-2）
- 任务依赖关系（Phase 4-2 前置）
- 邮件发送报告（未规划）

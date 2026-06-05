<p align="center">
  <h1 align="center">🌬️ Breeze</h1>
  <p align="center">
    <strong>带 AI Agent 的智能项目管理系统</strong>
    <br />
    类 Jira/Linear 体验 · 看板 · Sprint · 甘特图 · 12 个 AI 工具
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-brightgreen?logo=springboot" alt="Spring Boot 3.3" />
  <img src="https://img.shields.io/badge/React-18-blue?logo=react" alt="React 18" />
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql" alt="PostgreSQL 16" />
  <img src="https://img.shields.io/badge/pgvector-HNSW-4169E1" alt="pgvector" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis" alt="Redis 7" />
  <img src="https://img.shields.io/badge/Docker-一键部署-2496ED?logo=docker" alt="Docker" />
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License" />
</p>

---

## ✨ 核心功能

### 📋 项目管理
- 项目 CRUD、归档/完成状态流转、多角色权限（**Admin / Manager / Member / Viewer** 四级）
- 成员管理、角色分配、项目所有者保护
- 系统级角色 **SYSTEM_ADMIN** + 项目级角色分离

### 🎯 看板（Kanban Board）
- `@dnd-kit` 驱动拖拽，乐观更新 + API 失败自动回滚
- 默认 6 列（待规划 / 待处理 / 进行中 / 评审中 / 测试中 / 已完成）
- **自定义列**：增删改、颜色标签、WIP 限制、状态映射、列保护（核心列不可删除）
- Sprint 过滤模式、多条件筛选（状态/优先级/类型/指派人）

### 🔄 Sprint 管理
- Sprint 完整生命周期：创建 → 启动 → 关闭（未完成任务自动回退 Backlog）
- **燃尽图**（Story Points 基线 + 每日 ideal/actual 曲线）
- Sprint 看板、任务添加/移除
- 🤖 **AI 智能排期**：分析任务优先级、工时、成员负载，推荐排期方案

### 📊 甘特图（Gantt Chart）
- 基于 `frappe-gantt`，任务时间轴 + 依赖关系箭头
- 多时间粒度切换（Quarter Day / Half Day / Day / Week / Month）

### 📝 任务管理
- 完整任务模型：类型（Story/Bug/Task/Epic）、优先级（5 级）、状态流转、Story Points、工时预估
- **子任务**（单级嵌套）+ 子任务进度统计
- **任务协作者**（多对多，自动通知新增协作者）
- **任务依赖**：blocks / is_blocked_by / relates_to 三种关系
- **软删除** + 操作审计日志（Activity Log）

### 💬 评论与附件
- **富文本评论**：TipTap 编辑器，支持 Markdown、@提及、代码块、图片拖拽/粘贴
- 嵌套回复、DOMPurify XSS 防护
- **文件附件**：Local + S3 兼容双后端（MinIO / 阿里云 OSS），预签名 URL 上传下载

### 🔍 双引擎搜索
- **全文搜索**：PostgreSQL `tsvector` + GIN 索引
- **语义搜索**：pgvector HNSW 索引 + cosine 相似度，OpenAI `text-embedding-3-small` 向量化

### 🔔 通知系统
- 任务分配、协作者添加等 **6 种通知类型**
- **SSE 实时推送** + 站内通知列表 + 未读计数徽章

### 🛡️ 管理员面板（SYSTEM_ADMIN）
- 用户 CRUD、角色管理、启用/禁用、密码重置、强制改密
- **岗位管理**（Position）：8 个预设岗位，颜色标签，可自定义增删

### 📈 报表中心
- 日报 / 周报 / Sprint 报告，含统计卡片、趋势图、分布饼图、成员贡献表
- **PDF / CSV 导出**
- 🤖 **AI 报告生成**：周报 / Sprint 回顾 / 项目总结，Markdown 输出

### ⚡ 实时协作
- **SSE（Server-Sent Events）** 实时推送：任务变更广播给项目所有成员、通知即时推送
- 自动重连 + 指数退避、页面不可见时暂停

---

## 🤖 AI Agent 架构

```
用户输入 "帮我创建高优的支付超时 bug，分配给张三"
  │
  ├─→ AiAgentService.streamChat()
  │     ├─ RAG: pgvector 语义检索（Top 30）+ Sprint 上下文 + 用户任务
  │     ├─ 构建 System Prompt（12 个工具声明 + 项目信息 + 成员列表）
  │     ├─ 上下文窗口管理（16K token 上限，自动裁剪 + 摘要压缩）
  │     └─ ChatClient.prompt().tools(12 tools).stream()
  │           │
  │           ├─→ DeepSeek V4 Pro 分析意图
  │           ├─→ 调用 @Tool（读工具直接执行，写工具需用户确认）
  │           ├─→ SSE 流式返回文本 + 工具事件（Flux.merge）
  │           └─→ 前端 AiChatPanel 渲染（Markdown + 工具卡片 + 确认按钮）
```

### 12 个 AI 工具

| 分类 | 工具 | 功能 | 确认 |
|------|------|------|------|
| 📖 读 | `search_tasks` | 关键词 + 状态 + 指派人组合搜索 | — |
| 📖 读 | `list_members` | 获取项目成员列表 | — |
| 📖 读 | `get_task_detail` | 任务详情（含最近 10 条评论） | — |
| 📖 读 | `get_sprint_status` | Sprint 进度统计 | — |
| 📖 读 | `get_user_workload` | 按成员分组统计负载 | — |
| ✏️ 写 | `create_task` | 创建任务，自动映射人名/优先级/截止日期 | ✅ 需确认 |
| ✏️ 写 | `update_task` | 更新任务字段（标题/状态/优先级/截止日期等） | ✅ 需确认 |
| ✏️ 写 | `assign_task` | 分配/取消分配任务 | ✅ 需确认 |
| ✏️ 写 | `add_comment` | 添加 Markdown 评论 | — |
| ✏️ 写 | `create_subtasks` | 批量创建子任务 | ✅ 需确认 |
| ✏️ 写 | `add_to_sprint` | 将任务添加到 Sprint | ✅ 需确认 |
| ✏️ 写 | （预留） | 未来扩展更多工具 | — |

### 高级 AI 功能

| 功能 | 说明 |
|------|------|
| 🧩 **任务拆解** | AI 分析任务，流式生成子任务方案，预览后批量确认创建 |
| 📅 **智能排期** | 分析 Sprint 任务优先级 + 工时 + 成员负载，推荐排期 |
| ⚠️ **风险评估** | 5 维度规则引擎（关键词/描述完整度/截止日期/依赖链/负载），自动评分 |
| 📄 **报告生成** | 周报 / Sprint 回顾 / 项目总结，AI 收集上下文生成 Markdown |
| 🔎 **自然语言查询** | NL → SQL 自动转换 + 安全执行（仅 SELECT，强制 LIMIT） |
| 💾 **对话管理** | 历史保存、对话摘要自动生成、工具执行记录追踪 |

### AI 工程特性
- **上下文压缩**：超 20 条消息时 AI 自摘要，保持对话连贯性
- **Token 预算**：16K 上限，4K 保留给回复，超限自动裁剪历史消息
- **RAG 分层**：语义搜索结果（Top 30）+ 活跃 Sprint + 用户最近任务
- **Prompt 模板系统**：StringTemplate 4，`prompts/` 目录热加载，`$...$` 分隔符
- **工具事件总线**：Reactor `Sinks.Many`，按对话隔离，SSE 流融合

---

## 🏗️ 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | React 18 + TypeScript + Vite 6 | SPA 应用 |
| UI 组件 | Ant Design 5 + Tailwind CSS 3 | 玻璃拟态（Glassmorphism）视觉风格 |
| 状态管理 | Zustand 5 | 轻量响应式状态 |
| 拖拽 | @dnd-kit/core + sortable | 看板拖拽、列排序 |
| 富文本 | TipTap 3 + DOMPurify | Markdown 评论 + @提及 + 图片粘贴 |
| 甘特图 | frappe-gantt | 任务时间线可视化 |
| 图表 | Recharts | 燃尽图、报表统计图 |
| Markdown | react-markdown + remark-gfm | AI 消息渲染 |
| 后端框架 | Spring Boot 3.3 + Java 17 | RESTful API |
| ORM | MyBatis-Plus 3.5 | Lambda 查询 + XML Mapper + 分页 |
| AI 集成 | Spring AI 1.0 | ChatClient + @Tool Calling + PgVectorStore |
| 数据库 | PostgreSQL 16 + pgvector | 业务数据 + 全文搜索 + HNSW 向量索引 |
| 迁移 | Flyway | 17 个迁移脚本，20+ 张表 |
| 缓存 | Redis 7 | Session / 限流 / Refresh Token / 业务缓存 |
| 认证 | Spring Security + JWT | Access Token 1h + Refresh Token 7d + 自动刷新 |
| 实时通信 | SSE（Server-Sent Events） | 任务变更广播 + 通知推送，60s 心跳 |
| 文件存储 | S3 兼容（MinIO / 阿里云 OSS） | FileStorageService 抽象层，预签名 URL |
| 部署 | Docker Compose + Nginx | 一键部署，支持内外置数据库切换 |

---

## 🚀 快速开始

### 前置条件

- Docker & Docker Compose
- DeepSeek API Key（AI 对话必需）
- OpenAI API Key（语义搜索 Embedding，必需）

### 一键部署

```bash
# 克隆项目
git clone https://github.com/your-username/breeze.git
cd breeze

# 一键启动（内置 PostgreSQL + Redis + MinIO）
./start.sh --infra

# 或 Windows:
start.bat --infra
```

启动后访问：

| 服务 | 地址 |
|------|------|
| 🖥️ 前端页面 | http://localhost:80 |
| 🔧 后端 API | http://localhost:8080 |
| 📦 MinIO Console | http://localhost:9001 |

### 使用已有数据库

```bash
# 配置外部数据库
./start.sh --external
# 或直接编辑 .env：
#   DB_HOST=你的数据库地址
#   REDIS_HOST=你的 Redis 地址
# 然后：
docker compose up -d
```

### Docker 常用命令

> **注意：** postgres、redis、minio 在 `infra` profile 下，操作这三个服务时需要加 `--profile infra`。

```bash
# ---- 启停 ----
docker compose --profile infra up -d          # 启动全部服务
docker compose --profile infra down           # 关闭全部服务
docker compose --profile infra down -v        # 关闭全部 + 删除数据卷（数据库重置）

# ---- 按需操作指定容器 ----
docker compose stop backend                   # 只停后端
docker compose --profile infra stop redis     # 只停 redis
docker compose --profile infra restart postgres  # 只重启 postgres

# ---- 重新构建 ----
docker compose build --no-cache frontend      # 重新构建前端镜像
docker compose build --no-cache backend       # 重新构建后端镜像
docker compose --profile infra up -d --build  # 全部重新构建并启动

# ---- 日志 ----
docker compose logs -f backend                # 后端实时日志
docker compose logs -f --tail=100             # 全部服务最近 100 行
```

### 本地开发

<details>
<summary>展开开发环境搭建说明</summary>

**后端：**

```bash
cd backend
# 需要本地 PostgreSQL 16 + Redis 7
export DEEPSEEK_API_KEY=sk-your-key
export EMBEDDING_API_KEY=your-openai-key
export JWT_SECRET=your-secret

mvn spring-boot:run   # → http://localhost:8080
```

**前端：**

```bash
cd frontend
npm install
npm run dev            # → http://localhost:5173（/api 代理到 :8080）
```

</details>

---

## 📁 项目结构

```
breeze/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/breeze/
│   │   ├── activity/                 # 操作审计日志
│   │   ├── ai/                       # AI Agent（12 工具 + 5 高级服务 + Prompt 模板）
│   │   ├── attachment/               # 文件附件（Local + S3 双后端）
│   │   ├── auth/                     # 认证授权（JWT + Refresh Token + 限流）
│   │   ├── board/                    # 看板（列管理 + 状态映射 + WIP）
│   │   ├── comment/                  # 任务评论（嵌套回复）
│   │   ├── common/                   # 全局异常处理、分页、JSONB 类型处理
│   │   ├── config/                   # S3 存储配置、缓存配置
│   │   ├── dependency/               # 任务依赖关系
│   │   ├── event/                    # SSE 实时事件推送
│   │   ├── gantt/                    # 甘特图数据
│   │   ├── notification/             # 通知系统（6 种类型 + SSE 推送）
│   │   ├── project/                  # 项目 CRUD + 成员管理
│   │   ├── report/                   # 报表（日/周/Sprint + PDF/CSV 导出）
│   │   ├── sprint/                   # Sprint 管理（燃尽图 + 智能排期）
│   │   └── task/                     # 任务 CRUD + 全文/语义搜索 + 协作者
│   ├── src/main/resources/
│   │   ├── db/migration/             # 17 个 Flyway 迁移脚本（20+ 张表）
│   │   └── prompts/                  # AI Prompt 模板（StringTemplate 4）
│   └── src/test/
├── frontend/                         # React 前端
│   ├── src/
│   │   ├── api/                      # 17 个 API 模块（Axios + JWT 自动刷新）
│   │   ├── components/               # 30+ 共享组件
│   │   │   └── ai/                   # AI 聊天面板（流式渲染 + 工具卡片）
│   │   ├── hooks/                    # useRealtimeEvents、usePolling 等
│   │   ├── pages/                    # 17 个页面组件
│   │   ├── stores/                   # Zustand（auth / kanban / notification）
│   │   ├── styles/                   # 全局样式 + frappe-gantt 主题
│   │   └── constants/                # 枚举常量（优先级/类型/角色/岗位）
│   └── nginx.conf                    # Nginx（API 代理 + WebSocket + 安全头 + Gzip）
├── openspec/                         # OpenSpec 变更记录（项目文档）
│   └── changes/                      # 10 个 Change（proposal + design + tasks + specs）
├── docs/
│   ├── ARCHITECTURE.md               # 架构设计文档
│   ├── superpowers/                  # 实现计划 + 设计规范 + mockup
│   └── preview/                      # 设计预览 HTML
├── scripts/                          # 开发脚本（通知测试等）
├── docker-compose.yml                # Docker 编排（infra profile 可选）
├── start.sh                          # 一键部署脚本 (Linux/macOS)
├── start.bat                         # 一键部署脚本 (Windows)
├── .env.example                      # 环境变量模板
└── .github/                          # CI/CD + Issue/PR 模板
```

---

## 📊 功能矩阵

| 功能 | 后端 | 前端 | 说明 |
|------|:---:|:---:|------|
| 用户认证 + 权限 | ✅ | ✅ | JWT 双 Token + 系统/项目双角色体系 |
| 项目 CRUD + 成员 | ✅ | ✅ | 4 级角色 + 成员管理 |
| 看板拖拽 | ✅ | ✅ | @dnd-kit 乐观更新 + WIP 限制 |
| 任务 CRUD + 子任务 | ✅ | ✅ | 完整字段 + 协作者 + 依赖 |
| Sprint + 燃尽图 | ✅ | ✅ | 生命周期管理 + Recharts 可视化 |
| 甘特图 | ✅ | ✅ | frappe-gantt 时间线 + 依赖箭头 |
| 全文 + 语义搜索 | ✅ | ✅ | tsvector + pgvector HNSW |
| 评论 + 附件 | ✅ | ✅ | TipTap 富文本 + S3 预签名 URL |
| 通知 + 实时推送 | ✅ | ✅ | 6 种通知 + SSE + 未读计数 |
| 报表 + 导出 | ✅ | ✅ | 日/周/Sprint + PDF/CSV |
| 管理员面板 | ✅ | ✅ | 用户管理 + 岗位管理 |
| AI 对话助手 | ✅ | ✅ | SSE 流式 + RAG + 12 工具 |
| AI 任务拆解 | ✅ | ✅ | 流式生成 + 预览确认 |
| AI 智能排期 | ✅ | ✅ | 负载分析 + 日期推荐 |
| AI 风险评估 | ✅ | ✅ | 5 维规则引擎 + 批量评估 |
| AI 报告生成 | ✅ | ✅ | 3 种类型 + Markdown 输出 |
| AI 自然语言查询 | ✅ | ✅ | NL→SQL + 安全执行 |
| 操作审计日志 | ✅ | ✅ | 项目活动时间线 |

---

## 🔒 安全

- JWT 双 Token 机制（Access 1h + Refresh 7d + 轮换撤销）
- BCrypt 密码哈希 + 首次登录强制改密
- AI 端点限流（每用户每分钟 20 条，登录 5 次/分钟）
- AI 写操作需用户在前端确认，实现 `PendingToolAction` 执行屏障
- NL 查询仅允许 SELECT，强制 LIMIT 100
- DOMPurify 评论 XSS 防护
- **所有 API Key 通过环境变量注入，不写入配置文件**

详见 [SECURITY.md](SECURITY.md)

---

## 🤝 贡献

欢迎贡献代码、报告 Bug 或提出新功能建议！

- [CONTRIBUTING.md](CONTRIBUTING.md) — 开发流程 + Commit 规范
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — 社区准则
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — 系统架构设计
- [openspec/](openspec/) — 变更提案与设计决策记录

## 📄 许可证

MIT License — 详见 [LICENSE](LICENSE)

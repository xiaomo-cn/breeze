# Breeze 项目管理系统架构计划

## Context

从零构建一个带 AI Agent 功能的团队项目管理系统（类似 Jira/Linear），支持多用户协作、看板管理、Sprint 规划、甘特图、AI 智能助手。技术栈已确认：React + TypeScript（前端）、Spring Boot + PostgreSQL + pgvector（后端）、纯云端 AI API。

## 1. 技术栈总览

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 前端框架 | React 18 + TypeScript, Vite 5 构建 | |
| UI 组件 | Ant Design 5 + Tailwind CSS | Ant Design 组件丰富，Tailwind 处理布局细节 |
| 状态管理 | Zustand | 轻量，无模板代码 |
| 拖拽 | @dnd-kit | 现代化、可访问的 DnD 库 |
| 图表 | Recharts (burndown), frappe-gantt (甘特图) | |
| Markdown | @tiptap/react | 可扩展的富文本编辑器 |
| 后端框架 | **Spring Boot 3.3 + Java 21** | Java 工程师首选，生态最全 |
| AI 集成 | **Spring AI 1.0+** | 统一 ChatClient、Tool Calling、VectorStore、SSE 流式 |
| 数据库 | **PostgreSQL 16 + pgvector** | 业务数据 + 向量搜索 + 全文搜索，一套数据库全部搞定 |
| RAG 未来升级 | **Elasticsearch 8.x**（可选） | 数据量大时切换，Spring AI VectorStore 接口下换依赖即可，改动量极小 |
| 缓存 | Redis 7.x | Session、限流、Pub/Sub |
| 文件存储 | **阿里云 OSS (S3 兼容)** → 抽象 FileStorageService | 生产用 OSS，开发用 MinIO，同一套 S3 SDK |
| 认证 | Spring Security + JWT (jjwt) | Access Token 1h + Refresh Token 7d |
| 实时通信 | Spring WebSocket + STOMP | 看板协作、通知推送 |
| API 文档 | SpringDoc OpenAPI 2.x (Swagger UI) | |
| ORM | **MyBatis-Plus 3.5+** | |
| 部署 | Docker Compose + Nginx | |

## 2. 系统架构

### 2.1 整体架构

```
React SPA (Vite) --> Nginx (静态资源 + /api/* + /ws/* 反向代理)
                         |
              Spring Boot 3.3 (8080)
              ┌───────────┼───────────┐
              │           │           │
         Spring AI    Spring Security   MyBatis-Plus
         (ChatClient,  (JWT Filter,     (BaseMapper,
          Tool Calling,  RBAC)           LambdaQuery
          VectorStore                    + 分页插件)
          → pgvector)
              │           │           │
              v           v           v
    ┌──────────┬──────────┬──────────────┐
    │          │          │              │
PostgreSQL 16  Redis 7.x  MinIO / OSS    外部 AI API
(pgvector     (缓存/Session (S3 兼容存储)  (Claude/OpenAI)
 全文搜索     /限流/PubSub)
 向量存储)
    └──────────┴──────────┴──────────────┘
```

### 2.2 PostgreSQL 承担三重角色

| 角色 | 实现 | 说明 |
|------|------|------|
| 业务数据库 | 常规表 + JSONB | 用户、项目、任务、评论等所有业务数据 |
| 向量存储（RAG） | **pgvector** 插件 | 任务 Embedding 存储在 `task_embeddings` 表，cosine 相似度检索 |
| 全文搜索 | PostgreSQL 内置 `tsvector` + GIN 索引 | 任务标题/描述的全文搜索，中文可用 `zhparser` 分词或 `pg_bigm` |

### 2.3 为什么先用 PostgreSQL，不是 MySQL？

| 对比维度 | PostgreSQL 16 + pgvector | MySQL 8.0 |
|---------|------------------------|-----------|
| 向量搜索 | pgvector 原生支持，IVFFlat/HNSW 索引 | 不支持（9.0+ 才考虑加向量） |
| 全文搜索 | 内置 tsvector，中英文分词 | 内置 FULLTEXT，仅 InnoDB，中文弱 |
| JSON | JSONB 类型，支持索引 | JSON 类型，功能弱于 JSONB |
| Spring AI 适配 | `spring-ai-pgvector-store` 官方支持 | 无 VectorStore 实现 |
| Java 开发体验 | MyBatis-Plus/JPA 均可，Flyway 迁移，JSONB 支持 | MyBatis-Plus/JPA 均可，Flyway 迁移，但无向量能力 |

> **结论**：PostgreSQL 作为主库，一套解决所有问题。MySQL 在向量搜索方面缺失关键能力，如果选 MySQL 还得额外搭 ES/Redis Stack 做 RAG，增加运维负担。

### 2.4 后续如何切换到 Elasticsearch？

Spring AI 的 `VectorStore` 接口让切换成本极低：

```java
// 当前：pgvector
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate) {
    return new PgVectorStore(jdbcTemplate, embeddingModel);
}

// 未来切换到 ES：只改 Bean 定义，依赖换 spring-ai-elasticsearch-store
@Bean
public VectorStore vectorStore(ElasticsearchClient esClient) {
    return new ElasticsearchVectorStore(esClient, embeddingModel);
}
```

切换步骤：
1. `pom.xml`：替换 starter 依赖（`pgvector-store` → `elasticsearch-store`）
2. 修改一个 `@Bean` 配置
3. 将现有 Embedding 数据迁移到 ES（一批 SQL + API 调用）
4. 重启生效——所有调用 `VectorStore` 的代码**零改动**

引擎规模建议：任务量 < 10 万时 pgvector 完全够用，超过后再考虑切 ES。

### 2.5 项目结构

```
breeze/
├── backend/                 # Spring Boot 项目
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/breeze/
│   │   │   │   ├── BreezeApplication.java
│   │   │   │   ├── common/          # 基础实体、DTO、工具类
│   │   │   │   ├── auth/            # 认证授权
│   │   │   │   ├── project/         # 项目管理
│   │   │   │   ├── task/            # 任务管理
│   │   │   │   ├── kanban/          # 看板
│   │   │   │   ├── sprint/          # Sprint 规划
│   │   │   │   ├── report/          # 报表
│   │   │   │   ├── notification/    # 通知
│   │   │   │   └── ai/              # AI Agent
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/    # Flyway 迁移脚本
│   │   └── test/
│   └── docker-compose.yml          # PostgreSQL + Redis + MinIO
│
├── frontend/                # React + Vite 项目
│   ├── package.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/             # API 客户端层
│       ├── stores/          # Zustand 状态
│       ├── hooks/           # 自定义 Hook
│       ├── components/      # 共享组件
│       ├── pages/           # 页面组件
│       ├── routes/          # 路由配置
│       └── styles/          # 全局样式
│
├── docs/                    # 项目文档
├── ARCHITECTURE.md          # 本文档
└── .claude/                 # Claude Code 配置
```

## 3. 项目结构和模块规划

### 3.1 ORM 选型：MyBatis-Plus

**核心依赖**：
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>3.5.9</version>
</dependency>
```

**开发模式**：
- 业务 CRUD：`BaseMapper<T>` + Lambda 查询（`LambdaQueryWrapper`）
- 复杂查询：使用 **XML Mapper** 统一管理 SQL（便于 DBA 审查和调优）
- 分页：MyBatis-Plus 分页插件
- pgvector 向量操作：在 XML Mapper 中编写向量相似度 SQL

**与 Spring AI pgvector 的兼容性**：
Spring AI 的 `PgVectorStore` 使用 `JdbcTemplate` 直接操作数据库，不依赖任何 ORM。所以业务用 MyBatis-Plus、向量检索用 `JdbcTemplate`，两者互不干扰。

### 3.2 后端包结构

Phase 0（快速原型）用单模块 Spring Boot，后续按包边界拆分为多模块：

```
backend/src/main/java/com/breeze/
├── BreezeApplication.java
├── common/          # 基础实体、DTO、枚举、工具类
├── auth/            # 认证授权: 登录、JWT、RBAC
├── project/         # 项目 CRUD、成员管理
├── task/            # 任务 CRUD、分配、评论、附件、标签、依赖
├── kanban/          # 看板: 列管理、拖拽
├── sprint/          # Sprint: 规划、燃尽图
├── report/          # 报表: 日/周/冲刺报告、PDF/CSV 导出
├── notification/    # 通知: 站内、WebSocket 推送
└── ai/              # AI Agent: Spring AI 编排、RAG(pgvector)、Tool Calling
```

Phase 1 之后，可按需拆分为多模块 Maven 项目（common/auth/project/task/kanban/sprint/report/notification/ai/server），当前先保持简单。

## 4. 核心数据库表（PostgreSQL）

### 4.1 核心业务表

**users** — id, username, email, password_hash, display_name, avatar_url, title, department, timezone, locale, is_active

**projects** — id, name, key(唯一标识如 "CASES"), description, icon_url, status(active/archived/completed), visibility, owner_id, start_date, end_date

**project_members** — id, project_id, user_id, role(admin/manager/member/viewer), joined_at, 唯一约束(project_id, user_id)

**tasks** — id, project_id, parent_id(子任务), key(如 CASES-42), title, description(Markdown), type(story/bug/task/epic/subtask), status(backlog/todo/in_progress/in_review/done/cancelled), priority(lowest/low/medium/high/urgent), assignee_id, reporter_id, sprint_id, story_points, estimated_hours, logged_hours, due_date, started_at, resolved_at, sort_order, kanban_column_id, risk_level, risk_reason, is_deleted(软删除)

**task_comments** — id, task_id, author_id, parent_id(嵌套回复), content(Markdown), comment_type(user/system/ai), is_edited

**task_attachments** — id, task_id, uploader_id, file_name, file_size, mime_type, storage_path, thumbnail_path

**task_tags** + **task_tag_mappings** — 标签管理（每个项目自定义标签）

**task_dependencies** — task_id, depends_on_id, dependency_type(blocks/relates_to/duplicates)

**sprints** — id, project_id, name, goal, start_date, end_date, status, velocity_planned, velocity_actual, sort_order

**kanban_boards** — id, project_id(唯一), name, filter_config(JSON)

**kanban_columns** — id, board_id, name, mapped_status, color, wip_limit, sort_order

### 4.2 AI 和通知表

**notifications** — id, recipient_id, actor_id, type(11种类型枚举), title, content, entity_type, entity_id, is_read, is_email_sent

**ai_conversations** — id, user_id, project_id, title, model, context_snapshot(JSON)

**ai_messages** — id, conversation_id, role(user/assistant/system/tool), content, tool_calls(JSON), tool_call_id, tool_name, token_count, metadata(JSON)

**ai_tool_executions** — id, message_id, tool_name, tool_input(JSON), tool_output(JSON), status, duration_ms

**activity_log** — id, project_id, user_id, action, entity_type, entity_id, old_value(JSON), new_value(JSON)

## 5. API 设计概要

Base URL: `/api/v1`

| 模块 | 核心端点 |
|------|---------|
| Auth | POST `/auth/register`, `/auth/login`, `/auth/refresh`, GET `/auth/me` |
| Users | GET `/users`, `/users/{id}`, `/users/suggestions` |
| Projects | CRUD `/projects`, `/projects/{id}/members`, `/projects/{id}/stats`, `/projects/{id}/activity` |
| Tasks | CRUD `/projects/{pid}/tasks`, 子资源: comments, attachments, dependencies |
| Kanban | `/projects/{pid}/kanban/board`, columns CRUD, PATCH move task |
| Sprints | CRUD `/projects/{pid}/sprints`, start/close, burndown data, `/projects/{pid}/gantt` |
| Reports | GET daily/weekly/sprint reports, POST AI generate report, GET export (PDF/CSV) |
| Notifications | GET list/unread-count, PATCH mark read, WebSocket push |
| **AI Agent** | POST `/ai/chat` (SSE 流式), `/ai/breakdown/{taskId}`, `/ai/suggestions/scheduling`, `/ai/risks`, `/ai/nl-query` (SSE), `/ai/conversations` CRUD |

## 6. 文件存储方案（阿里云 OSS + S3 兼容）

### 6.1 抽象层设计

核心思路：定义 `FileStorageService` 接口，开发环境用 MinIO，生产环境用阿里云 OSS。

```java
public interface FileStorageService {

    /**
     * 生成预签名上传 URL（前端直传，不经过后端）
     */
    String generatePresignedUploadUrl(String bucketName, String objectKey,
                                       Duration expiration);

    /**
     * 生成预签名下载/预览 URL（私有文件临时访问）
     */
    String generatePresignedDownloadUrl(String bucketName, String objectKey,
                                         Duration expiration);

    /**
     * 获取公开访问 URL（公开文件永久链接，如 CDN 加速域名）
     */
    String getPublicUrl(String bucketName, String objectKey);

    /**
     * 删除文件
     */
    void deleteObject(String bucketName, String objectKey);

    /**
     * 检查文件是否存在
     */
    boolean objectExists(String bucketName, String objectKey);
}
```

### 6.2 实现方案

因为阿里云 OSS 和 MinIO **都兼容 AWS S3 协议**，用同一套 AWS S3 SDK 即可，仅 endpoint 配置不同。

```yaml
# application-dev.yml (开发环境 - MinIO)
file:
  storage:
    provider: s3
    endpoint: http://localhost:9000
    region: us-east-1
    access-key: minioadmin
    secret-key: minioadmin
    bucket-name: breeze-dev
    cdn-domain: http://localhost:9000/breeze-dev  # 开发无 CDN，直连 MinIO

# application-prod.yml (生产环境 - 阿里云 OSS)
file:
  storage:
    provider: s3                       # OSS 兼容 S3, 直接用 S3 SDK
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    region: oss-cn-hangzhou
    access-key: ${OSS_ACCESS_KEY}
    secret-key: ${OSS_SECRET_KEY}
    bucket-name: breeze-prod
    cdn-domain: https://cdn.breeze.com   # CDN 加速域名
```

如果后续需要阿里云 OSS 特有功能（如回调通知、图片处理），可单独加 `OssFileStorage` 实现，走阿里云 OSS SDK：

```xml
<!-- 可选，当需要 OSS 特有功能时可引入 -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
```

### 6.3 上传流程

```
1. 前端选择文件
2. 请求: POST /api/v1/projects/{pid}/tasks/{tid}/attachments/prepare
   Body: { fileName: "设计稿.png", fileSize: 204800, contentType: "image/png" }
3. 后端: 
   - 校验文件大小（≤50MB）
   - 生成 objectKey: projects/{pid}/tasks/{tid}/{uuid}_{fileName}
   - 记录 DB: task_attachments (status=pending)
   - 调用 FileStorageService.generatePresignedUploadUrl(objectKey, 5min)
4. 返回: { attachmentId, uploadUrl, objectKey }
5. 前端: 直接 PUT 文件到 uploadUrl (预签名 URL，不经过后端)
6. 前端: POST /api/v1/projects/{pid}/tasks/{tid}/attachments/{id}/confirm
7. 后端: 标记 attachment 状态为 uploaded
```

## 7. AI Agent 架构（基于 Spring AI）

### 7.1 Spring AI 核心依赖

```xml
<!-- Spring AI BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 二选一，或两者都引入以支持运行时切换 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

<!-- RAG: pgvector VectorStore -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>
```

### 7.2 自然语言创建任务流程（Java 侧实现）

这是 AI Agent 最核心的场景：用户说"帮我创建一个高优的登录页面修复任务，分配给张三，周五前完成"，AI 自动创建。

```java
// 1. 定义 AI 工具 —— Spring AI 通过 @Tool 注解声明
@Component
public class TaskTools {

    private final TaskService taskService;
    private final UserService userService;
    private final ProjectService projectService;

    @Tool(name = "create_task", description = """
        创建一个新任务。需要提供项目ID、标题，可选提供描述、类型、优先级、
        指派人、截止日期。返回创建的任务详情（含任务 key）。
        """)
    public TaskDTO createTask(
            @ToolParam(description = "项目ID") Long projectId,
            @ToolParam(description = "任务标题") String title,
            @ToolParam(description = "任务描述（Markdown格式，可选）") String description,
            @ToolParam(description = "任务类型: story/bug/task/epic，默认task") String type,
            @ToolParam(description = "优先级: low/medium/high/urgent，默认medium") String priority,
            @ToolParam(description = "指派人用户名或显示名（可选）") String assigneeName,
            @ToolParam(description = "截止日期 yyyy-MM-dd（可选）") String dueDate) {
        return taskService.createFromAi(projectId, title, description,
                type, priority, assigneeName, dueDate);
    }

    @Tool(name = "search_tasks", description = "按关键词、状态、指派人等条件搜索任务")
    public List<TaskDTO> searchTasks(
            @ToolParam(description = "项目ID") Long projectId,
            @ToolParam(description = "搜索关键词（可选）") String keyword,
            @ToolParam(description = "任务状态过滤（可选）") String status,
            @ToolParam(description = "指派人名称过滤（可选）") String assigneeName) {
        return taskService.search(projectId, keyword, status, assigneeName);
    }

    @Tool(name = "update_task", description = "更新任务字段（状态、优先级、指派人等）")
    public TaskDTO updateTask(
            @ToolParam(description = "任务ID") Long taskId,
            @ToolParam(description = "要更新的字段名") String field,
            @ToolParam(description = "新值") String value) {
        return taskService.updateField(taskId, field, value);
    }

    @Tool(name = "list_members", description = "获取项目成员列表")
    public List<UserDTO> listMembers(
            @ToolParam(description = "项目ID") Long projectId) {
        return projectService.getMembers(projectId);
    }
}
```

```java
// 2. AiAgentService —— 编排对话
@Service
public class AiAgentService {

    private final ChatClient chatClient;           // Spring AI ChatClient
    private final VectorStore vectorStore;          // pgvector 向量检索
    private final TaskMapper taskMapper;             // MyBatis-Plus
    private final AiMessageMapper messageMapper;

    public AiAgentService(ChatClient.Builder chatClientBuilder,
                          TaskTools taskTools,
                          VectorStore vectorStore) {
        this.chatClient = chatClientBuilder
                .defaultTools(taskTools)
                .build();
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamChat(Long projectId, Long userId, String userMessage,
                                   Long conversationId) {
        return Flux.create(sink -> {
            String projectContext = buildRagContext(projectId, userMessage, userId);
            String systemPrompt = buildSystemPrompt(projectContext, userId);
            List<Message> history = loadConversationHistory(conversationId, 20);

            chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(sink::next)
                .doOnComplete(sink::complete)
                .doOnError(sink::error)
                .subscribe();
        });
    }

    private String buildRagContext(Long projectId, String userQuery, Long userId) {
        StringBuilder ctx = new StringBuilder();

        // 1. pgvector 语义搜索
        ctx.append("=== 相关任务 ===\n");
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.query(userQuery).withTopK(10));
        for (Document doc : results) {
            ctx.append(doc.getContent()).append("\n");
        }

        // 2. 用户自己的任务（MyBatis-Plus LambdaQueryWrapper）
        ctx.append("\n=== 你的任务 ===\n");
        List<Task> myTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, userId)
                .eq(Task::getProjectId, projectId));
        for (Task t : myTasks) {
            ctx.append(formatTaskSummary(t)).append("\n");
        }

        return ctx.toString();
    }
}
```

```java
// 3. Controller —— SSE 流式端点
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiAgentService aiAgentService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestBody AiChatRequest request,
            Authentication auth) {
        Long userId = authService.getUserId(auth);

        return aiAgentService.streamChat(
                request.getProjectId(),
                userId,
                request.getMessage(),
                request.getConversationId())
            .map(text -> ServerSentEvent.<String>builder()
                    .event("text")
                    .data(text)
                    .build());
    }
}
```

```yaml
# application.yml —— AI 配置
spring:
  ai:
    # 使用 Claude（也可切到 openai）
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-20250514
          temperature: 0.3
          max-tokens: 4096
    # pgvector 向量库配置
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1024  # text-embedding-3-small 输出维度
        index-type: hnsw
```

### 7.3 核心流程（用户说 → AI 做）

```
用户在聊天框输入: "帮我创一个高优的支付超时 bug，分配给李四, 周五前修好"

  1. React AiChatPanel → POST /api/v1/ai/chat (SSE)
  2. Spring Boot AiController
  3. AiAgentService.streamChat():
     ├─ RAG: pgvector 检索相关任务上下文
     ├─ 构建 System Prompt（项目信息 + 成员列表 + RAG 结果）
     ├─ 加载对话历史（最近 20 条）
     └─ ChatClient.prompt()
          .system(systemPrompt)
          .messages(history)
          .user("帮我创一个高优的支付超时 bug...")
          .tools(taskTools)          // Spring AI 自动生成 Function Definitions
          .stream()
  4. LLM 分析用户意图 → 调用 create_task tool:
     { projectId: 1, title: "修复支付超时问题", type: "bug",
       priority: "high", assigneeName: "李四", dueDate: "2026-05-29" }
  5. Spring AI 拦截 tool_calls → 执行 TaskTools.createTask()
  6. 任务创建成功 → 返回结果给 LLM
  7. LLM 生成最终回复: "已创建任务 CASES-42: 修复支付超时问题 [高优] 分配给李四"
  8. SSE 流式推送给前端 → 用户看到回复 + 任务卡片预览
```

### 7.4 AI 工具列表

**写工具（通过 Spring AI @Tool 声明）**：

| 工具名 | 描述 | 确认 |
|--------|------|------|
| create_task | 创建任务 | 需要 |
| update_task | 更新任务字段 | 需要 |
| assign_task | 分配任务给人 | 需要 |
| add_comment | 给任务添加 AI 评论 | 不需要 |
| create_subtasks | 批量创建子任务（需求拆解） | 需要 |
| add_to_sprint | 将任务加入 Sprint | 需要 |

**读工具**：

| 工具名 | 描述 |
|--------|------|
| search_tasks | 关键词+条件搜索任务 |
| get_task_detail | 获取任务详情（含评论/附件） |
| list_members | 获取项目成员 |
| get_sprint_status | Sprint 进度统计 |
| get_user_workload | 成员负载统计 |

### 7.5 RAG 详细方案（pgvector + Spring AI）

**核心依赖**：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>
```

**数据库表**：
```sql
CREATE EXTENSION IF NOT EXISTS vector;           -- 启用 pgvector 插件

CREATE TABLE task_embeddings (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,                    -- 编码的原文（title + description 摘要）
    embedding   vector(1024),                    -- text-embedding-3-small: 1024 维
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now()
);

-- HNSW 索引（写入稍慢，查询极快，适合 RAG 场景）
CREATE INDEX idx_task_embeddings_hnsw ON task_embeddings
  USING hnsw (embedding vector_cosine_ops);
```

**业务层：MyBatis-Plus Mapper**（业务 CRUD 用 MyBatis-Plus）：
```java
// Task.java —— 实体
@Data
@TableName("tasks")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private String status;
    private String priority;
    // ...
}

// TaskMapper.java —— MyBatis-Plus BaseMapper 提供基础 CRUD
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    // 简单查询：MyBatis-Plus LambdaQueryWrapper 搞定
    // 复杂查询：声明方法 → XML 实现

    List<Task> fullTextSearch(@Param("projectId") Long projectId,
                              @Param("keyword") String keyword,
                              @Param("limit") int limit);

    List<Task> vectorSearch(@Param("projectId") Long projectId,
                            @Param("queryEmbedding") String queryEmbedding,
                            @Param("topK") int topK);
}
```

```xml
<!-- TaskMapper.xml —— 复杂 SQL 统一在 XML 中管理 -->
<mapper namespace="cn.xiaomo.breeze.task.mapper.TaskMapper">

    <!-- PostgreSQL 全文搜索 -->
    <select id="fullTextSearch" resultType="cn.xiaomo.breeze.task.entity.Task">
        SELECT t.*, ts_rank(t.tsv, plainto_tsquery('simple', #{keyword})) AS rank
        FROM tasks t
        WHERE t.project_id = #{projectId}
          AND t.is_deleted = false
          AND t.tsv @@ plainto_tsquery('simple', #{keyword})
        ORDER BY rank DESC
        LIMIT #{limit}
    </select>

    <!-- pgvector 余弦相似度搜索（RAG 向量检索） -->
    <select id="vectorSearch" resultType="cn.xiaomo.breeze.task.entity.Task">
        SELECT t.*, 1 - (te.embedding &lt;=&gt; #{queryEmbedding}::vector) AS similarity
        FROM task_embeddings te
        JOIN tasks t ON t.id = te.task_id
        WHERE t.project_id = #{projectId}
        ORDER BY te.embedding &lt;=&gt; #{queryEmbedding}::vector
        LIMIT #{topK}
    </select>

</mapper>
```

**向量层：Spring AI PgVectorStore**（RAG 向量检索用 Spring AI，不经过 MyBatis-Plus）：
```java
@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                    EmbeddingModel embeddingModel) {
        return new PgVectorStore(jdbcTemplate, embeddingModel);
    }
}

@Service
public class RagService {

    private final VectorStore vectorStore;      // Spring AI 向量存储
    private final TaskMapper taskMapper;         // MyBatis-Plus 业务查询

    /**
     * 任务变更后，异步更新 Embedding 索引
     */
    @Async
    public void indexTask(Task task) {
        String content = buildEmbeddingContent(task);
        Document doc = new Document(content, Map.of("task_id", task.getId().toString()));
        vectorStore.add(List.of(doc));
    }

    /**
     * 语义搜索相关任务
     */
    public List<Document> searchRelevantTasks(Long projectId, String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(topK));
    }
}
```

**Embedding 模型配置**：
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small   # 1024 维，$0.02/1M tokens
```

**pgvector vs Elasticsearch 性能参考**：

| 数据量 | pgvector (HNSW) | Elasticsearch |
|--------|-----------------|---------------|
| < 1 万任务 | < 5ms | < 10ms |
| 1-10 万任务 | < 20ms | < 15ms |
| > 10 万任务 | 可能退化到 > 50ms | < 20ms |

> 结论：10 万任务以内 pgvector 完全够用，且运维成本极低。后续需要时切 ES，改一个 Bean 即可。

### 7.6 工具调用确认机制（写操作安全）

对于 `create_task`、`update_task` 等写工具，不直接执行：

```
Spring AI 拦截到 tool_calls
  → 判断是否为写工具
  → 写工具: 通过 SSE 发 tool_confirmation 事件给前端
     { toolCallId, toolName, params, preview: "将创建任务: 修复支付超时 [高优]" }
  → 用户点确认 → POST /ai/confirm-tool/{messageId}
  → 执行工具 → 将结果追加到 LLM 对话 → 继续生成回复
```

### 7.7 上下文窗口管理

- Token 预算：System 4K + RAG Context 8K + History 16K ≈ 28K，留 4K+ 给回复
- 对话压缩：>40 条消息时，用轻量模型（claude-haiku）对早期消息生成摘要
- RAG 分层：Layer 1 必含（项目信息、用户任务）→ Layer 2 相关（语义匹配）→ Layer 3 补充（成员、最近活动）

## 8. 前端路由和页面

```
/login, /register, /forgot-password    → 认证页面
/                                       → Dashboard (项目概览)
/projects/:key                          → 看板 (默认视图)
/projects/:key/backlog                  → 任务列表/Backlog
/projects/:key/sprints                  → Sprint 规划
/projects/:key/gantt                    → 甘特图视图
/projects/:key/reports                  → 报表中心
/projects/:key/settings                 → 项目设置/成员管理
/projects/:key/tasks/:taskId            → 任务详情页
/ai                                     → AI 助手全屏页
/profile                                → 个人设置
```

## 9. 实施阶段

### Phase 0: 快速原型（目标：2-3 天看到初步效果）

**目标**：用最小技术栈跑通核心链路"注册 → 登录 → 创建项目 → 创建任务 → AI 自然语言创建任务"

**后端**（单模块 Spring Boot，先不做多模块拆分）：
- 一个 `pom.xml`，包含所有依赖（Spring AI、MyBatis-Plus、Security、PostgreSQL、pgvector）
- `docker-compose.yml`：PostgreSQL 16 + Redis 7 + MinIO
- Flyway 迁移脚本：建表 + 启用 pgvector 扩展 + 创建向量索引
- JWT 认证（`/api/v1/auth/register`、`/api/v1/auth/login`）
- 项目 CRUD（`/api/v1/projects`）
- 任务 CRUD（`/api/v1/projects/{pid}/tasks`）
- **AI 对话端点**：`POST /api/v1/ai/chat`（SSE 流式），Spring AI ChatClient + `@Tool` 注解
- AI 工具：`create_task`、`search_tasks`、`list_members` 三个核心工具即可
- RAG：pgvector 向量检索相关任务上下文
- 写工具暂不需要确认（快速原型直接执行）

**前端**（最小可用）：
- Vite + React + TypeScript 项目
- Ant Design 5 组件库
- LoginPage、RegisterPage
- DashboardPage（项目列表）
- ProjectBoardPage（简易看板：Backlog / In Progress / Done 三列）
- AiChatPanel（固定在右下角的 AI 对话浮窗）
- SSE 流式接收（fetch + ReadableStream）

**验收标准**：
1. 浏览器注册用户 → 登录 → 创建项目
2. 在项目中手动创建任务，看板上可拖拽切换状态（@dnd-kit）
3. 在 AI 对话框输入"帮我创建一个任务：修复登录页面样式 bug，优先级高"
4. AI 回复并自动创建任务，看板上出现新卡片

### Phase 1: 基础设施 + 后端 MVP（Phase 0 基础上完善，预计 1-2 周）
- 完善单模块项目结构，补充 Flyway 迁移脚本覆盖所有表
- 完善 JWT 认证流程（Refresh Token + 刷新机制）
- 完善用户、项目、任务 CRUD API + 分页/过滤
- 统一 API 错误处理 + 参数校验
- PostgreSQL 全文搜索（tsvector）索引建立
- pgvector 向量索引 + 异步 Embedding 更新
- 集成测试

### Phase 2: 前端 Shell + 看板（预计 1-2 周）
- React + Vite 项目初始化，路由、状态管理
- 登录/注册页面
- AppLayout（Sidebar + Header）
- Dashboard 页面
- 看板页面（@dnd-kit 拖拽）
- TaskCard, TaskCreateForm, TaskDetailModal
- WebSocket 实时更新

### Phase 3: 协作功能（预计 2 周）
- 任务评论（Markdown 编辑）
- 文件附件上传（预签名 URL）
- 通知系统（站内 + WebSocket 推送）
- MinIO 文件存储
- Sprint 模块（规划、启动、关闭）
- 燃尽图

### Phase 4: 报表 + 甘特图（预计 1-2 周）
- 报表生成（日/周/冲刺统计）
- PDF/CSV 导出
- 甘特图组件
- 任务依赖关系

### Phase 5: AI Agent 核心（预计 2-3 周）
- breeze-ai 模块 + AI API 客户端抽象
- ContextRetriever RAG 上下文检索
- AiAgentOrchestrator 编排
- 读/写工具实现 + 确认流程
- SSE 流式响应
- 前端 AiChatPanel + 流式渲染 + 工具确认 UI
- 对话历史管理 + 上下文压缩
- AI 端点限流

### Phase 6: AI Agent 高级功能（预计 2 周）
- AI 需求拆解 + 前端结果预览
- 风险自动评估 + 面板
- 智能排期建议
- AI 报告生成
- 自然语言查询

### Phase 7: 打磨上线（预计 1-2 周）
- 性能优化、N+1 修复、Redis 缓存
- 移动端响应式（至少看板和任务视图）
- Playwright E2E 测试
- 安全审计
- Docker Compose 生产部署
- Prometheus + Grafana 监控

## 10. 关键技术决策

- **认证**：JWT（Access Token 1h + Refresh Token 7d），无状态 Session
- **实时协作**：WebSocket STOMP，看板操作乐观更新
- **文件存储**：`FileStorageService` 接口抽象；开发用 MinIO，生产用阿里云 OSS；两者都兼容 S3 协议，统一用 AWS S3 SDK，仅切换 endpoint 配置
- **搜索 + RAG**：PostgreSQL pgvector + tsvector 全文搜索；Spring AI `spring-ai-pgvector-store` 提供原生 VectorStore 适配；数据量大时可通过 VectorStore 接口平滑迁移到 Elasticsearch
- **AI 集成**：Spring AI 1.0+ 提供统一的 ChatClient、Tool Calling（`@Tool` 注解）、VectorStore 抽象、SSE 流式
- **AI 限流**：20条消息/分钟/用户，5次拆解/小时，100次/项目/小时
- **API 错误格式**：统一 `{ error, message, fieldErrors, retryAfterSeconds, timestamp }`
- **任务编号**：{PROJECT_KEY}-{自增数字}，通过 Redis INCR 实现
- **软删除**：核心实体使用 is_deleted 标志

## 11. 验证方式

1. Phase 1 完成后：Postman/curl 测试全套 Auth + CRUD API
2. Phase 2 完成后：浏览器验证登录→创建项目→看板拖拽→创建任务全流程
3. Phase 3 完成后：多用户协作测试（评论、通知、Sprint 规划）
4. Phase 5 完成后：AI 对话测试（提问项目状态、让 AI 创建任务、确认工具调用）
5. Phase 6 完成后：AI 高级功能测试（拆解需求、风险预警验证、报告生成质量评估）
6. Phase 7：Playwright 自动化回归 + OWASP 安全扫描

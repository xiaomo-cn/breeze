# Phase 5a: AI 核心升级实现计划

> **For agentic workers:** Use superpowers:subagent-driven-development to implement.

**Goal:** RAG 接入 pgvector 语义搜索 + 修复历史加载 bug + 丰富 System Prompt + 工具执行日志。

**Architecture:** RagService 新增 `semanticSearch()` 方法调用 pgvector cosine 距离查询；AiAgentService 修复历史加载顺序、System Prompt 加入 Sprint 上下文、工具调用记录到 ai_tool_executions。

**Tech Stack:** Spring Boot 3.3, MyBatis-Plus 3.5.9, Spring AI 1.0.0-M6, PostgreSQL pgvector

---

### Task 1: RagService 接入 pgvector 语义搜索

**Files:**
- Modify: `backend/src/main/java/com/breeze/ai/RagService.java`
- Modify: `backend/src/main/java/com/breeze/ai/EmbeddingService.java`

**Step 1: EmbeddingService 添加 embed 异常处理**

当前 `embed()` 方法有 `@SneakyThrows`，改进为显式异常处理：

```java
public float[] embed(String text) {
    try {
        String requestBody = objectMapper.writeValueAsString(
            Map.of("model", model, "input", text, "encoding_format", "float"));

        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/embeddings"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embedding = root.path("data").get(0).path("embedding");
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = (float) embedding.get(i).asDouble();
        }
        return result;
    } catch (Exception e) {
        throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
    }
}
```

（仅移除 `@SneakyThrows`，将异常包装为 RuntimeException）

**Step 2: RagService 添加语义搜索方法**

在 RagService 中注入 EmbeddingService 和 JdbcTemplate，添加：

```java
private final EmbeddingService embeddingService;
private final JdbcTemplate jdbcTemplate;

/**
 * 使用 pgvector 余弦距离搜索语义相关任务（最多 10 条）
 */
private List<Task> semanticSearch(Long projectId, String query) {
    try {
        float[] embedding = embeddingService.embed(query);
        String vectorStr = embeddingService.toVectorString(embedding);

        String sql = """
            SELECT t.* FROM tasks t
            JOIN task_embeddings te ON t.id = te.task_id
            WHERE t.project_id = ? AND t.is_deleted = false
            ORDER BY te.embedding <=> ?::vector
            LIMIT 10
            """;
        return jdbcTemplate.query(sql, new Object[]{projectId, vectorStr},
            (rs, rowNum) -> {
                Task task = new Task();
                task.setId(rs.getLong("id"));
                task.setKey(rs.getString("key"));
                task.setTitle(rs.getString("title"));
                task.setStatus(rs.getString("status"));
                task.setPriority(rs.getString("priority"));
                task.setProjectId(rs.getLong("project_id"));
                return task;
            });
    } catch (Exception e) {
        // 语义搜索失败时降级，返回空列表
        return List.of();
    }
}
```

**Step 3: 修改 buildRagContext 使用语义搜索**

将现有的"最近 10 个任务"替换为语义搜索结果（优先），并增加 Sprint 上下文：

```java
public String buildRagContext(Long projectId, String userQuery, Long userId) {
    StringBuilder ctx = new StringBuilder();

    Project project = projectService.getById(projectId);
    ctx.append("=== 项目信息 ===\n");
    ctx.append("项目: ").append(project.getName()).append("\n");

    // 1. 语义搜索结果（主要）
    if (userQuery != null && !userQuery.isBlank()) {
        List<Task> semanticTasks = semanticSearch(projectId, userQuery);
        if (!semanticTasks.isEmpty()) {
            ctx.append("\n=== 相关任务（语义搜索） ===\n");
            for (Task t : semanticTasks) {
                ctx.append("- ").append(t.getKey()).append(": ").append(t.getTitle())
                    .append(" [").append(t.getStatus()).append("]\n");
            }
        }
    }

    // 2. Sprint 上下文
    List<Sprint> activeSprints = sprintMapper.selectList(
        new LambdaQueryWrapper<Sprint>()
            .eq(Sprint::getProjectId, projectId)
            .eq(Sprint::getStatus, "active"));
    if (!activeSprints.isEmpty()) {
        ctx.append("\n=== 活跃 Sprint ===\n");
        for (Sprint s : activeSprints) {
            ctx.append("- ").append(s.getName())
                .append(" (").append(s.getStartDate()).append(" ~ ").append(s.getEndDate()).append(")\n");
            if (s.getGoal() != null) {
                ctx.append("  目标: ").append(s.getGoal()).append("\n");
            }
        }
    }

    // 3. 用户任务（保留）
    if (userId != null) {
        List<Task> myTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, userId)
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false));
        if (!myTasks.isEmpty()) {
            ctx.append("\n=== 你的任务 ===\n");
            for (Task t : myTasks) {
                ctx.append("- ").append(t.getKey()).append(": ").append(t.getTitle())
                    .append(" [").append(t.getStatus()).append("]\n");
            }
        }
    }

    return ctx.toString();
}
```

需要注入：`EmbeddingService`、`JdbcTemplate`、`SprintMapper`

---

### Task 2: AiAgentService 修复 + 增强

**Files:**
- Modify: `backend/src/main/java/com/breeze/ai/AiAgentService.java`

**Step 1: 修复 loadConversationHistory — 取最近 20 条**

当前 SQL 用 `orderByAsc + LIMIT 20`，实际取的是最早 20 条。改为取最近：

```java
private List<Message> loadConversationHistory(Long conversationId, int limit) {
    List<AiMessage> messages = messageMapper.selectList(
        new LambdaQueryWrapper<AiMessage>()
            .eq(AiMessage::getConversationId, conversationId)
            .orderByDesc(AiMessage::getCreatedAt)
            .last("LIMIT " + limit));

    // 按时间升序排列（最早在前，用于对话上下文）
    List<Message> history = new ArrayList<>();
    for (int i = messages.size() - 1; i >= 0; i--) {
        AiMessage msg = messages.get(i);
        if ("user".equals(msg.getRole())) {
            history.add(new UserMessage(msg.getContent()));
        } else if ("assistant".equals(msg.getRole())) {
            history.add(new AssistantMessage(msg.getContent()));
        }
    }
    return history;
}
```

**Step 2: 丰富 System Prompt**

在 buildSystemPrompt 中加入工具使用指引和项目统计：

```java
private String buildSystemPrompt(Long projectId, Long userId, String ragContext) {
    var project = projectService.getById(projectId);
    User user = userMapper.selectById(userId);

    StringBuilder sb = new StringBuilder();
    sb.append("你是 Breeze 的 AI 助手，帮助用户管理项目任务。\n\n");
    sb.append("当前项目: ").append(project.getName()).append("\n");
    sb.append("当前用户: ").append(user.getDisplayName()).append("\n");
    sb.append("当前日期: ").append(java.time.LocalDate.now()).append("\n\n");

    sb.append(ragContext);
    sb.append("\n");

    sb.append("=== 可用工具 ===\n");
    sb.append("- create_task: 创建新任务\n");
    sb.append("- search_tasks: 搜索任务\n");
    sb.append("- list_members: 查看项目成员\n\n");

    sb.append("指南:\n");
    sb.append("- 回答用中文，专业且简洁\n");
    sb.append("- 操作任务时确认关键信息（标题、指派人、优先级）\n");
    sb.append("- 如需搜索任务但用户未给关键词，主动使用 search_tasks\n");

    return sb.toString();
}
```

**Step 3: 移除 buildSystemPrompt 中重复的用户任务查询**

当前 buildSystemPrompt 中有独立的 myTasks 查询，但 RagService 的 buildRagContext 也已经包含了。去除 AiAgentService 中的重复查询（mytasks 部分），避免重复数据库查询。

---

### Task 3: 工具执行日志

**Files:**
- Modify: `backend/src/main/java/com/breeze/ai/TaskTools.java`
- Create: `backend/src/test/java/com/breeze/ai/TaskToolsTest.java`

**Step 1: TaskTools 注入 AiToolExecutionMapper 并记录日志**

```java
private final AiToolExecutionMapper toolExecutionMapper;

// 在每个 @Tool 方法开头添加：
private void logToolExecution(String toolName, String input, String output, String status, Long durationMs) {
    try {
        AiToolExecution exec = new AiToolExecution();
        exec.setToolName(toolName);
        exec.setToolInput(input);
        exec.setToolOutput(output);
        exec.setStatus(status);
        exec.setDurationMs(durationMs);
        exec.setCreatedAt(LocalDateTime.now());
        toolExecutionMapper.insert(exec);
    } catch (Exception e) {
        // 日志记录失败不影响主流程
    }
}
```

每个 @Tool 方法改为记录执行日志。示例 — `create_task`：

```java
@Tool(name = "create_task", description = "创建新任务...")
public Task createTask(...) {
    long start = System.currentTimeMillis();
    try {
        Task task = doCreateTask(projectId, title, description, type, priority, assigneeName, dueDate);
        logToolExecution("create_task",
            objectMapper.writeValueAsString(Map.of("projectId", projectId, "title", title)),
            objectMapper.writeValueAsString(Map.of("taskId", task.getId(), "key", task.getKey())),
            "success", System.currentTimeMillis() - start);
        return task;
    } catch (Exception e) {
        logToolExecution("create_task",
            objectMapper.writeValueAsString(Map.of("projectId", projectId, "title", title)),
            e.getMessage(), "error", System.currentTimeMillis() - start);
        throw e;
    }
}
```

需要注入：`AiToolExecutionMapper`、`ObjectMapper`

**Step 2: 创建 AiToolExecutionMapper（如果不存在）**

```java
package cn.xiaomo.breeze.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiToolExecutionMapper extends BaseMapper<AiToolExecution> {
}
```

**Step 3: 创建 AiToolExecution 实体（如果不存在）**

如果 AiToolExecution 实体不存在，创建它。检查现有包中是否已有此实体。

---

### Task 4: 验证

- 后端编译通过
- 所有现有测试通过
- 语义搜索降级不阻断主流程（Embedding API 不可用时）

---

## 不包含（Phase 5b/c/d）

- 新工具的添加（5b）
- 工具确认机制（5c）
- 对话历史前端（5c）
- Token 计数和压缩（5d）

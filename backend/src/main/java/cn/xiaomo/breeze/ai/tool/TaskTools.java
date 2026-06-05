package cn.xiaomo.breeze.ai.tool;

import cn.xiaomo.breeze.ai.entity.AiToolExecution;
import cn.xiaomo.breeze.ai.entity.PendingToolAction;
import cn.xiaomo.breeze.ai.mapper.AiToolExecutionMapper;
import cn.xiaomo.breeze.ai.mapper.PendingToolActionMapper;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import cn.xiaomo.breeze.project.ProjectService;
import cn.xiaomo.breeze.ai.service.PromptTemplateService;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import cn.xiaomo.breeze.task.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTools {

    private static final Set<String> VALID_TYPES = Set.of("story", "bug", "task", "epic", "subtask");
    private static final Set<String> VALID_PRIORITIES = Set.of("low", "medium", "high", "critical");
    /** AI 操作使用系统用户 ID */
    private static final long AI_USER_ID = 0L;

    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final UserMapper userMapper;
    private final ProjectMemberMapper memberMapper;
    private final AiToolExecutionMapper toolExecutionMapper;
    private final PendingToolActionMapper pendingActionMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ToolEventPublisher eventPublisher;
    private final PromptTemplateService promptTemplateService;

    @Tool(name = "create_task", description = "创建新任务。需提供项目ID和标题。可选：描述、类型、优先级、指派人、截止日期")
    public String createTask(
            @ToolParam(description = "项目ID") Long projectId,
            @ToolParam(description = "任务标题") String title,
            @ToolParam(description = "任务描述（Markdown，可选）") String description,
            @ToolParam(description = "类型: story/bug/task/epic，默认task") String type,
            @ToolParam(description = "优先级: low/medium/high/critical，默认medium") String priority,
            @ToolParam(description = "指派人用户名或显示名（可选）") String assigneeName,
            @ToolParam(description = "截止日期 yyyy-MM-dd（可选）") String dueDate,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String inputSummary = "title=" + title + ", projectId=" + projectId;
        // 发布工具开始事件
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "create_task",
            "正在创建任务: " + title);
        // 参数校验
        if (title == null || title.isBlank()) {
            return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary, "标题不能为空", start);
        }
        String resolvedType = (type != null && !type.isBlank()) ? type.toLowerCase() : "task";
        if (!VALID_TYPES.contains(resolvedType)) {
            return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary,
                "无效的类型 '" + type + "'，有效值: " + String.join(", ", VALID_TYPES), start);
        }
        String resolvedPriority = (priority != null && !priority.isBlank()) ? priority.toLowerCase() : "medium";
        if (!VALID_PRIORITIES.contains(resolvedPriority)) {
            return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary,
                "无效的优先级 '" + priority + "'，有效值: " + String.join(", ", VALID_PRIORITIES), start);
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description != null ? description : "");
        task.setType(resolvedType);
        task.setPriority(resolvedPriority);

        if (assigneeName != null && !assigneeName.isBlank()) {
            User assignee = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, assigneeName)
                .or().eq(User::getDisplayName, assigneeName));
            if (assignee == null) {
                return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary,
                    "未找到成员 '" + assigneeName + "'，请使用 list_members 查看项目成员", start);
            }
            task.setAssigneeId(assignee.getId());
        }

        if (dueDate != null && !dueDate.isBlank()) {
            try {
                task.setDueDate(LocalDate.parse(dueDate));
            } catch (DateTimeParseException e) {
                return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary,
                    "日期格式错误 '" + dueDate + "'，请使用 yyyy-MM-dd 格式", start);
            }
        }

        // 写操作默认需要确认（ToolContext 不传递此配置，始终为 true）
        if (true) {
            return requireConfirmation(ToolContextUtils.getConvId(toolContext), projectId, title, task, inputSummary, start);
        }

        try {
            Task created = taskService.create(projectId, task, AI_USER_ID);
            String result = "✅ 任务 " + created.getKey() + " 已创建：\"" + title + "\""
                + "，优先级: " + resolvedPriority
                + "，类型: " + resolvedType
                + (assigneeName != null ? "，指派人: " + assigneeName : "");
            logToolExecution("create_task", inputSummary,
                "taskId=" + created.getId() + ", key=" + created.getKey(), "success",
                System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "create_task", "任务 " + created.getKey() + " 已创建", true);
            return result;
        } catch (Exception e) {
            return error(ToolContextUtils.getConvId(toolContext), "create_task", inputSummary, e.getMessage(), start);
        }
    }

    @Tool(name = "search_tasks", description = "按关键词、状态、指派人搜索任务")
    public String searchTasks(
            @ToolParam(description = "项目ID") Long projectId,
            @ToolParam(description = "搜索关键词（可选）") String keyword,
            @ToolParam(description = "任务状态过滤（可选）") String status,
            @ToolParam(description = "指派人名称（可选）") String assigneeName,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String inputSummary = "projectId=" + projectId + ", keyword=" + keyword + ", status=" + status;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "search_tasks",
            keyword != null ? "正在搜索: " + keyword : "正在搜索任务");

        try {
            List<Task> result;

            if (keyword != null && !keyword.isBlank()) {
                // 使用全文搜索（ILIKE + 排序 + LIMIT）
                result = taskMapper.fulltextSearch(projectId, keyword, 50);
            } else {
                // 无关键词时用 MyBatis-Plus 构建查询
                LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                    .eq(Task::getProjectId, projectId)
                    .eq(Task::getIsDeleted, false)
                    .orderByDesc(Task::getUpdatedAt)
                    .last("LIMIT 50");
                result = taskMapper.selectList(wrapper);
            }

            // 状态过滤
            if (status != null && !status.isBlank()) {
                result = result.stream()
                    .filter(t -> status.equals(t.getStatus()))
                    .toList();
            }

            // 指派人过滤
            if (assigneeName != null && !assigneeName.isBlank()) {
                User assignee = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, assigneeName)
                    .or().eq(User::getDisplayName, assigneeName));
                if (assignee != null) {
                    Long aid = assignee.getId();
                    result = result.stream()
                        .filter(t -> aid.equals(t.getAssigneeId()))
                        .toList();
                } else {
                    result = List.of();
                }
            }

            if (result.isEmpty()) {
                logToolExecution("search_tasks", inputSummary, "no results", "success",
                    System.currentTimeMillis() - start);
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "search_tasks", "未找到匹配的任务", true);
                return "未找到匹配的任务";
            }

            List<Map<String, String>> taskRows = new ArrayList<>();
            for (Task t : result) {
                taskRows.add(Map.of(
                    "key", t.getKey(),
                    "title", t.getTitle(),
                    "status", t.getStatus(),
                    "priority", t.getPriority() != null ? t.getPriority() : "-"
                ));
            }
            Map<String, Object> vars = new HashMap<>();
            vars.put("count", result.size());
            vars.put("tasks", taskRows);
            vars.put("hasMore", result.size() >= 50);

            String output = promptTemplateService.render("tools/tool-search-tasks", vars);
            logToolExecution("search_tasks", inputSummary,
                "found=" + result.size() + " tasks", "success",
                System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "search_tasks", "找到 " + result.size() + " 个任务", true);
            return output;
        } catch (Exception e) {
            return error(ToolContextUtils.getConvId(toolContext), "search_tasks", inputSummary, e.getMessage(), start);
        }
    }

    @Tool(name = "list_members", description = "获取项目成员列表")
    public String listMembers(
            @ToolParam(description = "项目ID") Long projectId,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String inputSummary = "projectId=" + projectId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "list_members",
            "正在获取项目成员列表");

        try {
            // 先查项目成员 ID 列表
            List<Long> userIds = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, projectId))
                .stream()
                .map(ProjectMember::getUserId)
                .toList();

            if (userIds.isEmpty()) {
                logToolExecution("list_members", inputSummary, "no members", "success",
                    System.currentTimeMillis() - start);
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "list_members", "该项目暂无成员", true);
                return "该项目暂无成员";
            }

            // 批量查询用户信息（只需 2 次 SQL，而非 N+1 次）
            List<User> users = userMapper.selectByIds(userIds);

            List<Map<String, String>> memberRows = new ArrayList<>();
            for (User u : users) {
                memberRows.add(Map.of(
                    "name", u.getDisplayName(),
                    "username", u.getUsername(),
                    "email", u.getEmail() != null ? u.getEmail() : "-"
                ));
            }
            String result = promptTemplateService.render("tools/tool-list-members",
                Map.of("count", users.size(), "members", memberRows));
            logToolExecution("list_members", inputSummary,
                "found=" + users.size() + " members", "success",
                System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "list_members", "获取到 " + users.size() + " 个成员", true);
            return result;
        } catch (Exception e) {
            return error(ToolContextUtils.getConvId(toolContext), "list_members", inputSummary, e.getMessage(), start);
        }
    }

    /**
     * 创建待确认操作并发布确认事件。
     */
    private String requireConfirmation(Long convId, Long projectId, String title, Task task,
                                        String inputSummary, long start) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("title", title);
        params.put("description", task.getDescription());
        params.put("type", task.getType());
        params.put("priority", task.getPriority());
        if (task.getAssigneeId() != null) params.put("assigneeId", task.getAssigneeId());
        if (task.getDueDate() != null) params.put("dueDate", task.getDueDate().toString());

        PendingToolAction pending = new PendingToolAction();
        pending.setConversationId(convId != null ? convId : 0L);
        pending.setToolName("create_task");
        pending.setDescription("创建任务: " + title + " [优先级: " + task.getPriority() + "]");
        pending.setParamsJson(params);
        pending.setStatus("pending");
        pending.setCreatedAt(java.time.LocalDateTime.now());
        pendingActionMapper.insert(pending);

        String confirmDetails = "标题: " + title
            + "\n优先级: " + task.getPriority()
            + "\n类型: " + task.getType()
            + (task.getDescription() != null && !task.getDescription().isBlank()
                ? "\n描述: " + task.getDescription().substring(0, Math.min(100, task.getDescription().length())) : "");

        eventPublisher.publishConfirmation(convId, "create_task",
            "确认创建任务: " + title, pending.getId().toString(), confirmDetails);

        String result = "⏳ 等待确认: 创建任务 \"" + title + "\"（优先级: " + task.getPriority() + "）";
        logToolExecution("create_task", inputSummary,
            "pending_confirmation, pendingId=" + pending.getId(), "pending",
            System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 记录工具执行并返回错误消息。同时发布工具事件。
     */
    private String error(Long convId, String toolName, String input, String message, long start) {
        logToolExecution(toolName, input, message, "error", System.currentTimeMillis() - start);
        publishToolEvent(convId, toolName, message, false);
        return "❌ " + message;
    }

    /**
     * 发布工具事件到当前对话的 SSE 流。
     */
    private void publishToolEvent(Long convId, String toolName, String message, boolean success) {
        if (convId != null) {
            if (success) {
                eventPublisher.publishEnd(convId, toolName, message, true);
            } else {
                eventPublisher.publishEnd(convId, toolName, message, false);
            }
        }
    }

    private void logToolExecution(String toolName, String input, String output,
                                   String status, Long durationMs) {
        try {
            AiToolExecution exec = new AiToolExecution();
            exec.setToolName(toolName);
            exec.setToolInput(Map.of("summary", input));
            exec.setToolOutput(Map.of("summary", output));
            exec.setStatus(status);
            exec.setDurationMs(durationMs != null ? durationMs.intValue() : 0);
            exec.setUserId(AI_USER_ID);
            exec.setCreatedAt(java.time.LocalDateTime.now());
            toolExecutionMapper.insert(exec);
        } catch (Exception e) {
            log.error("Failed to log tool execution: tool={}, status={}", toolName, status, e);
        }
    }
}

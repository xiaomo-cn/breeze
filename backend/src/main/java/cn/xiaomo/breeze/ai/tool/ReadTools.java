package cn.xiaomo.breeze.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.ai.entity.AiToolExecution;
import cn.xiaomo.breeze.ai.mapper.AiToolExecutionMapper;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.comment.Comment;
import cn.xiaomo.breeze.comment.CommentMapper;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.sprint.SprintService;
import cn.xiaomo.breeze.sprint.dto.SprintDTO;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import cn.xiaomo.breeze.ai.service.PromptTemplateService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 读操作工具集 — 任务详情、Sprint 进度、成员负载。
 * 所有工具返回 {@link String} 人类可读的格式化文本，而非领域对象，
 * 避免 Spring AI 序列化复杂对象，让 LLM 获得清晰反馈。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadTools {

    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final SprintService sprintService;
    private final SprintMapper sprintMapper;
    private final CommentMapper commentMapper;
    private final AiToolExecutionMapper toolExecutionMapper;
    private final ToolEventPublisher eventPublisher;
    private final PromptTemplateService promptTemplateService;


    @Tool(name = "get_task_detail", description = "获取任务完整信息：标题、描述、状态、优先级、指派人、评论列表")
    public String getTaskDetail(
            @ToolParam(description = "任务ID") Long taskId,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "taskId=" + taskId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "get_task_detail",
            "正在获取任务详情");
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_task_detail", "任务不存在", false);
                return "错误: 任务不存在";
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("key", task.getKey());
            vars.put("title", task.getTitle());
            vars.put("description", task.getDescription() != null && !task.getDescription().isBlank()
                ? task.getDescription() : "无");
            vars.put("status", task.getStatus());
            vars.put("priority", task.getPriority() != null ? task.getPriority() : "未设置");
            vars.put("type", task.getType());

            if (task.getAssigneeId() != null) {
                User assignee = userMapper.selectById(task.getAssigneeId());
                if (assignee != null) vars.put("assignee", assignee.getDisplayName());
            }
            if (task.getDueDate() != null) vars.put("dueDate", task.getDueDate().toString());
            if (task.getEstimatedHours() != null) vars.put("estimatedHours", task.getEstimatedHours().toString());
            if (task.getSprintId() != null) {
                Sprint sprint = sprintMapper.selectById(task.getSprintId());
                if (sprint != null) vars.put("sprintName", sprint.getName());
            }

            // 评论列表
            List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getTaskId, taskId)
                    .orderByDesc(Comment::getCreatedAt)
                    .last("LIMIT 10"));
            if (!comments.isEmpty()) {
                List<Map<String, String>> commentRows = new ArrayList<>();
                for (Comment c : comments) {
                    User author = userMapper.selectById(c.getUserId());
                    String authorName = author != null ? author.getDisplayName() : "未知";
                    String content = c.getContent() != null && c.getContent().length() > 100
                        ? c.getContent().substring(0, 100) + "..." : c.getContent();
                    content = content != null ? content.replace("|", "\\|") : "";
                    commentRows.add(Map.of("author", authorName, "content", content));
                }
                vars.put("comments", commentRows);
            }

            String result = promptTemplateService.render("tools/tool-task-detail", vars);
            logToolExecution("get_task_detail", input, "ok", "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_task_detail", "已获取任务 " + task.getKey() + " 的详情", true);
            return result;
        } catch (Exception e) {
            logToolExecution("get_task_detail", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_task_detail", e.getMessage(), false);
            return "获取任务详情失败: " + e.getMessage();
        }
    }

    @Tool(name = "get_sprint_status", description = "获取 Sprint 进度：已完成/总量、完成率、燃尽趋势")
    public String getSprintStatus(
            @ToolParam(description = "Sprint ID") Long sprintId,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "sprintId=" + sprintId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "get_sprint_status",
            "正在获取 Sprint 进度");
        try {
            SprintDTO sprint = sprintService.getById(sprintId);
            if (sprint == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_sprint_status", "Sprint 不存在", false);
                return "错误: Sprint 不存在";
            }

            // SprintDTO 的 taskCount 和 completedTaskCount 是原始 int，不会为 null
            int completed = sprint.getCompletedTaskCount();
            int total = sprint.getTaskCount();
            int percent = total > 0 ? Math.round((float) completed / total * 100) : 0;

            Map<String, Object> vars = new HashMap<>();
            vars.put("name", sprint.getName());
            vars.put("status", sprint.getStatus());
            vars.put("progress", completed + "/" + total + " 已完成 (" + percent + "%)");
            if (sprint.getGoal() != null) vars.put("goal", sprint.getGoal());
            if (sprint.getStartDate() != null && sprint.getEndDate() != null) {
                vars.put("dateRange", sprint.getStartDate() + " ~ " + sprint.getEndDate());
            }

            String result = promptTemplateService.render("tools/tool-sprint-status", vars);
            logToolExecution("get_sprint_status", input, "ok", "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_sprint_status", completed + "/" + total + " 已完成", true);
            return result;
        } catch (Exception e) {
            logToolExecution("get_sprint_status", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_sprint_status", e.getMessage(), false);
            return "获取 Sprint 状态失败: " + e.getMessage();
        }
    }

    @Tool(name = "get_user_workload", description = "获取项目成员当前任务负载，按指派人分组统计")
    public String getUserWorkload(
            @ToolParam(description = "项目ID") Long projectId,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "projectId=" + projectId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "get_user_workload",
            "正在获取成员工作负载");
        try {
            List<Task> activeTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                    .eq(Task::getProjectId, projectId)
                    .eq(Task::getIsDeleted, false)
                    .in(Task::getStatus, "todo", "in_progress", "review")
                    .isNotNull(Task::getAssigneeId));

            Map<Long, List<Task>> byAssignee = activeTasks.stream()
                .collect(Collectors.groupingBy(Task::getAssigneeId));
            Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

            if (byAssignee.isEmpty()) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_user_workload", "当前没有任务", true);
                return "当前没有分配给成员的任务";
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (var entry : byAssignee.entrySet()) {
                String name = userNames.getOrDefault(entry.getKey(), "用户" + entry.getKey());
                Map<String, Long> byStatus = entry.getValue().stream()
                    .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
                rows.add(Map.of(
                    "name", name,
                    "total", entry.getValue().size(),
                    "todo", byStatus.getOrDefault("todo", 0L),
                    "inProgress", byStatus.getOrDefault("in_progress", 0L),
                    "review", byStatus.getOrDefault("review", 0L)
                ));
            }
            String result = promptTemplateService.render("tools/tool-workload",
                Map.of("rows", rows));
            logToolExecution("get_user_workload", input, "ok", "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_user_workload", byAssignee.size() + " 个成员有活跃任务", true);
            return result;
        } catch (Exception e) {
            logToolExecution("get_user_workload", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "get_user_workload", e.getMessage(), false);
            return "获取负载失败: " + e.getMessage();
        }
    }

    private void publishToolEvent(Long convId, String toolName, String message, boolean success) {
        if (convId != null) {
            eventPublisher.publishEnd(convId, toolName, message, success);
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
            exec.setUserId(0L);
            exec.setCreatedAt(java.time.LocalDateTime.now());
            toolExecutionMapper.insert(exec);
        } catch (Exception e) {
            log.error("Failed to log tool execution: tool={}, status={}", toolName, status, e);
        }
    }
}

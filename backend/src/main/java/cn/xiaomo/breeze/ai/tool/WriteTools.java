package cn.xiaomo.breeze.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.ai.entity.AiToolExecution;
import cn.xiaomo.breeze.ai.mapper.AiToolExecutionMapper;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.comment.CommentService;
import cn.xiaomo.breeze.comment.dto.CreateCommentRequest;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.sprint.SprintService;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import cn.xiaomo.breeze.task.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 写操作工具集 — 更新、分配、评论、子任务、Sprint 管理。
 * 所有工具返回 {@link String} 人类可读的结果描述，而非领域对象，
 * 避免 Spring AI 序列化复杂对象，让 LLM 获得清晰反馈。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriteTools {

    private final TaskMapper taskMapper;
    private final TaskService taskService;
    private final UserMapper userMapper;
    private final SprintService sprintService;
    private final SprintMapper sprintMapper;
    private final CommentService commentService;
    private final AiToolExecutionMapper toolExecutionMapper;
    private final ObjectMapper objectMapper;
    private final ToolEventPublisher eventPublisher;


    @Tool(name = "update_task", description = "更新任务字段。需提供任务ID。可选：标题、描述、状态、优先级、类型、截止日期、预估工时")
    public String updateTask(
            @ToolParam(description = "任务ID") Long taskId,
            @ToolParam(description = "新标题（可选）") String title,
            @ToolParam(description = "新描述（可选）") String description,
            @ToolParam(description = "新状态: todo/in_progress/review/done（可选）") String status,
            @ToolParam(description = "新优先级: low/medium/high/critical（可选）") String priority,
            @ToolParam(description = "截止日期 yyyy-MM-dd（可选）") String dueDate,
            @ToolParam(description = "预估工时（小时，可选）") Integer estimatedHours,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "taskId=" + taskId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "update_task",
            "正在更新任务");
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "update_task", "任务不存在", false);
                return "错误: 任务不存在";
            }

            if (title != null && !title.isBlank()) task.setTitle(title);
            if (description != null && !description.isBlank()) task.setDescription(description);
            if (status != null && !status.isBlank()) task.setStatus(status);
            if (priority != null && !priority.isBlank()) task.setPriority(priority);
            if (dueDate != null && !dueDate.isBlank()) task.setDueDate(LocalDate.parse(dueDate));
            if (estimatedHours != null) task.setEstimatedHours(BigDecimal.valueOf(estimatedHours));

            taskMapper.updateById(task);
            String result = "任务 " + task.getKey() + " 已更新";
            logToolExecution("update_task", input, result, "success", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            logToolExecution("update_task", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            return "更新失败: " + e.getMessage();
        }
    }

    @Tool(name = "assign_task", description = "将任务分配给指定成员。如不指定成员名则取消分配")
    public String assignTask(
            @ToolParam(description = "任务ID") Long taskId,
            @ToolParam(description = "成员用户名或显示名") String assigneeName,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "taskId=" + taskId + ", assignee=" + assigneeName;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "assign_task",
            assigneeName != null ? "正在分配给 " + assigneeName : "正在取消分配");
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "assign_task", "任务不存在", false);
                return "错误: 任务不存在";
            }

            if (assigneeName == null || assigneeName.isBlank()) {
                task.setAssigneeId(null);
            } else {
                User assignee = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, assigneeName)
                    .or().eq(User::getDisplayName, assigneeName));
                if (assignee == null) {
                    publishToolEvent(ToolContextUtils.getConvId(toolContext), "assign_task", "未找到成员 " + assigneeName, false);
                    return "错误: 未找到成员 " + assigneeName;
                }
                task.setAssigneeId(assignee.getId());
            }

            taskMapper.updateById(task);
            String result = assigneeName != null && !assigneeName.isBlank()
                ? "任务 " + task.getKey() + " 已分配给 " + assigneeName
                : "任务 " + task.getKey() + " 已取消分配";
            logToolExecution("assign_task", input, result, "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "assign_task", result, true);
            return result;
        } catch (Exception e) {
            logToolExecution("assign_task", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "assign_task", e.getMessage(), false);
            return "分配失败: " + e.getMessage();
        }
    }

    @Tool(name = "add_comment", description = "给任务添加评论，支持 Markdown 格式")
    public String addComment(
            @ToolParam(description = "任务ID") Long taskId,
            @ToolParam(description = "评论内容（支持 Markdown）") String content,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "taskId=" + taskId + ", content="
            + (content != null ? content.substring(0, Math.min(50, content.length())) : "");
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "add_comment",
            "正在添加评论");
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_comment", "任务不存在", false);
                return "错误: 任务不存在";
            }

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent(content);
            commentService.create(taskId, req, 0L); // userId=0 代表 AI

            String result = "评论已添加到任务 " + task.getKey();
            logToolExecution("add_comment", input, result, "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_comment", result, true);
            return result;
        } catch (Exception e) {
            logToolExecution("add_comment", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_comment", e.getMessage(), false);
            return "添加评论失败: " + e.getMessage();
        }
    }

    @Tool(name = "create_subtasks", description = "为父任务批量创建子任务")
    public String createSubtasks(
            @ToolParam(description = "父任务ID") Long parentTaskId,
            @ToolParam(description = "子任务标题列表，JSON 数组，如 [\"子任务1\", \"子任务2\"]") String titlesJson,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "parentTaskId=" + parentTaskId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "create_subtasks",
            "正在创建子任务");
        try {
            Task parent = taskMapper.selectById(parentTaskId);
            if (parent == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "create_subtasks", "父任务不存在", false);
                return "错误: 父任务不存在";
            }

            @SuppressWarnings("unchecked")
            List<String> titles = objectMapper.readValue(titlesJson, List.class);
            int created = 0;
            for (Object t : titles) {
                Task sub = new Task();
                sub.setTitle(t.toString());
                sub.setType("subtask");
                sub.setParentId(parentTaskId);
                sub.setPriority("medium");
                taskService.create(parent.getProjectId(), sub, 0L);
                created++;
            }

            String result = "已创建 " + created + " 个子任务";
            logToolExecution("create_subtasks", input, result, "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "create_subtasks", result, true);
            return result;
        } catch (Exception e) {
            logToolExecution("create_subtasks", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "create_subtasks", e.getMessage(), false);
            return "创建子任务失败: " + e.getMessage();
        }
    }

    @Tool(name = "add_to_sprint", description = "将任务添加到指定 Sprint")
    public String addToSprint(
            @ToolParam(description = "任务ID") Long taskId,
            @ToolParam(description = "Sprint ID") Long sprintId,
            ToolContext toolContext) {

        long start = System.currentTimeMillis();
        String input = "taskId=" + taskId + ", sprintId=" + sprintId;
        eventPublisher.publishStart(ToolContextUtils.getConvId(toolContext), "add_to_sprint",
            "正在添加到 Sprint");
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_to_sprint", "任务不存在", false);
                return "错误: 任务不存在";
            }

            sprintService.addTask(sprintId, taskId);
            Sprint sprint = sprintMapper.selectById(sprintId);

            String result = "任务 " + task.getKey() + " 已添加到 Sprint "
                + (sprint != null ? sprint.getName() : sprintId.toString());
            logToolExecution("add_to_sprint", input, result, "success", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_to_sprint", result, true);
            return result;
        } catch (Exception e) {
            logToolExecution("add_to_sprint", input, e.getMessage(), "error", System.currentTimeMillis() - start);
            publishToolEvent(ToolContextUtils.getConvId(toolContext), "add_to_sprint", e.getMessage(), false);
            return "添加到 Sprint 失败: " + e.getMessage();
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

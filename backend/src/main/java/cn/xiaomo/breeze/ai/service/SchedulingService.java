package cn.xiaomo.breeze.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 智能排期服务 — 基于 AI 为 Sprint 任务生成排期建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final TaskMapper taskMapper;
    private final SprintMapper sprintMapper;
    private final ObjectMapper objectMapper;

    /**
     * 生成 Sprint 排期建议。
     *
     * @param sprintId Sprint ID
     * @return 排期建议列表，每项包含 taskId、suggestedAssigneeId、suggestedStartDate 等
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> suggestScheduling(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) {
            throw new IllegalArgumentException("Sprint not found: " + sprintId);
        }

        List<Task> sprintTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, sprintId)
                .eq(Task::getIsDeleted, false));

        // 构建数据上下文
        StringBuilder ctx = new StringBuilder();
        ctx.append("Sprint: ").append(sprint.getName()).append("\n");
        ctx.append("日期: ").append(sprint.getStartDate()).append(" ~ ")
            .append(sprint.getEndDate()).append("\n\n");
        ctx.append("待排期任务:\n");
        for (Task t : sprintTasks) {
            ctx.append("- ").append(t.getKey()).append(": ").append(t.getTitle())
                .append(" [优先级: ").append(t.getPriority())
                .append(", 预估: ").append(t.getEstimatedHours()).append("h")
                .append(", Story Points: ").append(t.getStoryPoints())
                .append("]\n");
        }

        // 获取项目成员当前负载
        List<Task> activeProjectTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, sprint.getProjectId())
                .eq(Task::getIsDeleted, false)
                .in(Task::getStatus, "todo", "in_progress", "review")
                .isNotNull(Task::getAssigneeId));

        Map<Long, Long> workload = new HashMap<>();
        for (Task t : activeProjectTasks) {
            workload.merge(t.getAssigneeId(), 1L, Long::sum);
        }

        ctx.append("\n成员当前负载（活跃任务数）:\n");
        for (var e : workload.entrySet()) {
            ctx.append("- userId:").append(e.getKey())
                .append(" = ").append(e.getValue()).append("个任务\n");
        }

        String prompt = promptTemplateService.render("scheduling-prompt", Map.of(
                "sprintContext", ctx.toString()
        ));

        String response = chatClient.prompt().user(prompt).call().content();

        try {
            String json = response;
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("Failed to parse scheduling response: sprintId={}, response={}", sprintId, response, e);
            return List.of(Map.of(
                "error", "Failed to parse AI response",
                "raw", response));
        }
    }
}

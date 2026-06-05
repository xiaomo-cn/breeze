package cn.xiaomo.breeze.ai.service;

import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import cn.xiaomo.breeze.task.TaskService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * AI 任务拆解服务。
 * 分析任务描述，生成子任务树，支持预览后批量创建。
 */
@Service
@RequiredArgsConstructor
public class BreakdownService {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    /**
     * 流式生成子任务拆解建议。
     *
     * @param taskId 要拆解的任务 ID
     * @return SSE 流，包含 AI 生成的子任务 JSON
     */
    public Flux<String> generateBreakdown(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return Flux.error(new IllegalArgumentException("任务未找到: " + taskId));
        }

        String prompt = buildBreakdownPrompt(task);

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    private String buildBreakdownPrompt(Task task) {
        return promptTemplateService.render("breakdown-prompt", Map.of(
                "title", task.getTitle(),
                "description", task.getDescription() != null ? task.getDescription() : "无",
                "type", task.getType() != null ? task.getType() : "task"
        ));
    }

    /**
     * 批量创建子任务（仅一级，不嵌套）。
     *
     * @param parentTaskId 父任务 ID
     * @param subtasks     子任务节点列表
     * @return 创建的所有任务列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Task> batchCreate(Long parentTaskId, List<Map<String, Object>> subtasks, Long userId) {
        Task parent = taskMapper.selectById(parentTaskId);
        if (parent == null) {
            throw new IllegalArgumentException("父任务未找到: " + parentTaskId);
        }

        List<Task> created = new ArrayList<>();
        for (var st : subtasks) {
            Task task = new Task();
            task.setTitle((String) st.get("title"));
            task.setType(st.get("type") != null ? (String) st.get("type") : "subtask");
            task.setPriority(st.get("priority") != null ? (String) st.get("priority") : "medium");
            task.setParentId(parentTaskId);
            if (st.get("estimatedHours") != null) {
                task.setEstimatedHours(
                        new BigDecimal(String.valueOf(st.get("estimatedHours"))));
            }
            Task saved = taskService.create(parent.getProjectId(), task, userId);
            created.add(saved);
        }
        return created;
    }
}

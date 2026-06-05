package cn.xiaomo.breeze.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import cn.xiaomo.breeze.project.Project;
import cn.xiaomo.breeze.project.ProjectService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final SprintMapper sprintMapper;
    private final VectorStore vectorStore;
    private final PromptTemplateService promptTemplateService;

    public String buildRagContext(Long projectId, String userQuery, Long userId) {
        Project project = projectService.getById(projectId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("projectName", project.getName());
        vars.put("projectId", projectId.toString());

        // 1. 语义搜索结果
        if (userQuery != null && !userQuery.isBlank()) {
            List<Task> semanticTasks = semanticSearch(projectId, userQuery);
            if (!semanticTasks.isEmpty()) {
                List<Map<String, String>> taskRows = new ArrayList<>();
                for (Task t : semanticTasks) {
                    taskRows.add(Map.of(
                        "key", t.getKey(),
                        "title", t.getTitle(),
                        "status", t.getStatus(),
                        "priority", t.getPriority() != null ? t.getPriority() : "-"
                    ));
                }
                vars.put("semanticTasks", taskRows);
            }
        }

        // 2. Sprint 上下文
        List<Sprint> activeSprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .eq(Sprint::getStatus, "active"));
        if (!activeSprints.isEmpty()) {
            List<Map<String, String>> sprintRows = new ArrayList<>();
            for (Sprint s : activeSprints) {
                sprintRows.add(Map.of(
                    "name", s.getName(),
                    "dateRange", s.getStartDate() + " ~ " + s.getEndDate(),
                    "goal", s.getGoal() != null ? s.getGoal() : ""
                ));
            }
            vars.put("activeSprints", sprintRows);
        }

        // 3. 用户任务
        if (userId != null) {
            List<Task> myTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                    .eq(Task::getAssigneeId, userId)
                    .eq(Task::getProjectId, projectId)
                    .eq(Task::getIsDeleted, false)
                    .orderByDesc(Task::getUpdatedAt)
                    .last("LIMIT 15"));
            if (!myTasks.isEmpty()) {
                List<Map<String, String>> taskRows = new ArrayList<>();
                for (Task t : myTasks) {
                    taskRows.add(Map.of(
                        "key", t.getKey(),
                        "title", t.getTitle(),
                        "status", t.getStatus()
                    ));
                }
                vars.put("myTasks", taskRows);
            }
        }

        return promptTemplateService.render("tools/tool-rag-context", vars);
    }

    private List<Task> semanticSearch(Long projectId, String query) {
        try {
            var docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(query)
                    .topK(30)
                    .build()
            );

            List<Long> taskIds = docs.stream()
                .filter(doc -> {
                    Object pid = doc.getMetadata().get("project_id");
                    return pid instanceof Number
                        && ((Number) pid).longValue() == projectId.longValue();
                })
                .map(doc -> {
                    Object tid = doc.getMetadata().get("task_id");
                    return tid instanceof Number ? ((Number) tid).longValue() : null;
                })
                .filter(id -> id != null)
                .limit(10)
                .toList();

            if (taskIds.isEmpty()) {
                return List.of();
            }

            return taskMapper.selectByIds(taskIds);
        } catch (Exception e) {
            log.error("Semantic search failed: projectId={}, query={}", projectId, query, e);
            return List.of();
        }
    }
}

package cn.xiaomo.breeze.ai.service;

import cn.xiaomo.breeze.activity.ActivityLog;
import cn.xiaomo.breeze.activity.ActivityLogMapper;
import cn.xiaomo.breeze.ai.entity.AiReport;
import cn.xiaomo.breeze.ai.mapper.AiReportMapper;
import cn.xiaomo.breeze.project.Project;
import cn.xiaomo.breeze.project.ProjectService;
import cn.xiaomo.breeze.sprint.SprintService;
import cn.xiaomo.breeze.sprint.dto.SprintDTO;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI 报告生成服务。
 * 汇聚项目数据上下文，交由 AI 生成结构化的 Markdown 报告。
 */
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final ProjectService projectService;
    private final TaskMapper taskMapper;
    private final SprintService sprintService;
    private final ActivityLogMapper activityLogMapper;
    private final AiReportMapper aiReportMapper;

    /**
     * 生成 AI 报告并持久化保存。
     *
     * @param type      报告类型：weekly / sprint_review / project_summary
     * @param projectId 项目 ID
     * @return 保存后的 AiReport 实体
     */
    public AiReport generate(String type, Long projectId) {
        String dataContext = buildDataContext(type, projectId);
        String prompt = buildPrompt(type, dataContext);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String title = switch (type) {
            case "weekly" -> "项目周报 - " + LocalDate.now();
            case "sprint_review" -> "Sprint 回顾 - " + LocalDate.now();
            case "project_summary" -> "项目总结 - " + LocalDate.now();
            default -> "AI 报告 - " + LocalDate.now();
        };

        AiReport report = new AiReport();
        report.setProjectId(projectId);
        report.setType(type);
        report.setTitle(title);
        report.setContent(content);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        aiReportMapper.insert(report);

        return report;
    }

    /**
     * 查询项目下指定类型的历史报告。
     *
     * @param projectId 项目 ID
     * @param type      报告类型
     * @return 历史报告列表（按生成时间倒序，最多 10 条）
     */
    public List<AiReport> listByProjectAndType(Long projectId, String type) {
        return aiReportMapper.selectList(
            new LambdaQueryWrapper<AiReport>()
                .eq(AiReport::getProjectId, projectId)
                .eq(AiReport::getType, type)
                .orderByDesc(AiReport::getGeneratedAt)
                .last("LIMIT 10"));
    }

    /**
     * 按 ID 获取单个报告。
     */
    public AiReport getById(Long id) {
        return aiReportMapper.selectById(id);
    }

    /**
     * 构建数据上下文，为 AI 提供项目当前状态的全景视图。
     */
    private String buildDataContext(String type, Long projectId) {
        Project project = projectService.getById(projectId);
        StringBuilder ctx = new StringBuilder();
        ctx.append("项目: ").append(project.getName()).append("\n");

        // 任务统计
        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getIsDeleted, false));

        long total = allTasks.size();
        long done = allTasks.stream().filter(t -> "done".equals(t.getStatus())).count();
        long inProgress = allTasks.stream().filter(t -> "in_progress".equals(t.getStatus())).count();
        long todo = allTasks.stream().filter(t -> "todo".equals(t.getStatus())).count();

        ctx.append("\n任务总数: ").append(total);
        ctx.append("\n已完成: ").append(done);
        ctx.append("\n进行中: ").append(inProgress);
        ctx.append("\n待办: ").append(todo);
        ctx.append("\n完成率: ").append(total > 0 ? Math.round((float) done / total * 100) : 0).append("%\n");

        // 按状态列出任务
        ctx.append("\n--- 进行中任务 ---\n");
        allTasks.stream().filter(t -> "in_progress".equals(t.getStatus()))
                .forEach(t -> ctx.append("- ").append(t.getKey()).append(": ").append(t.getTitle()).append("\n"));

        ctx.append("\n--- 高风险任务 ---\n");
        allTasks.stream().filter(t -> "high".equals(t.getRiskLevel()))
                .forEach(t -> ctx.append("- ").append(t.getKey()).append(": ").append(t.getTitle())
                        .append(" [").append(t.getRiskReason()).append("]\n"));

        // Sprint 信息
        List<SprintDTO> sprints = sprintService.listByProject(projectId);
        if (!sprints.isEmpty()) {
            ctx.append("\n--- Sprint 状态 ---\n");
            for (SprintDTO s : sprints) {
                ctx.append("- ").append(s.getName()).append(": ")
                        .append(s.getCompletedTaskCount()).append("/").append(s.getTaskCount())
                        .append(" [").append(s.getStatus()).append("]\n");
            }
        }

        // 本周活动
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        List<ActivityLog> activities = activityLogMapper.selectList(
                new LambdaQueryWrapper<ActivityLog>()
                        .eq(ActivityLog::getProjectId, projectId)
                        .ge(ActivityLog::getCreatedAt, weekAgo)
                        .orderByDesc(ActivityLog::getCreatedAt)
                        .last("LIMIT 20"));
        if (!activities.isEmpty()) {
            ctx.append("\n--- 本周活动 ---\n");
            for (ActivityLog a : activities) {
                ctx.append("- ").append(a.getActionType()).append(" ").append(a.getEntityType())
                        .append(" (").append(a.getCreatedAt()).append(")\n");
            }
        }

        return ctx.toString();
    }

    /**
     * 构建 AI 提示词。
     */
    private String buildPrompt(String type, String dataContext) {
        String reportType = switch (type) {
            case "weekly" -> "项目周报";
            case "sprint_review" -> "Sprint 回顾报告";
            case "project_summary" -> "项目总结报告";
            default -> "项目报告";
        };

        return promptTemplateService.render("report-prompt", Map.of(
                "reportType", reportType,
                "dataContext", dataContext
        ));
    }
}

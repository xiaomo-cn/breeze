package cn.xiaomo.breeze.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.dependency.TaskDependency;
import cn.xiaomo.breeze.dependency.TaskDependencyMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基于规则的任务风险评估服务。
 * 从标题关键词、描述完整度、截止日期紧迫度、
 * 依赖链复杂度、指派人负载五个维度综合评分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;

    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
        "紧急", "生产bug", "崩溃", "故障", "安全漏洞", "数据丢失", "宕机", "线上"
    );
    private static final Set<String> MEDIUM_RISK_KEYWORDS = Set.of(
        "性能", "优化", "重构", "兼容", "升级"
    );

    /**
     * 评估单个任务的风险等级并更新数据库。
     */
    public void assess(Task task) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // 1. 标题关键词
        String title = task.getTitle() != null ? task.getTitle() : "";
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (title.contains(kw)) {
                score += 30;
                reasons.add("标题包含高风险关键词: " + kw);
                break;
            }
        }
        if (score < 30) {
            for (String kw : MEDIUM_RISK_KEYWORDS) {
                if (title.contains(kw)) {
                    score += 15;
                    reasons.add("标题包含中风险关键词: " + kw);
                    break;
                }
            }
        }

        // 2. 描述过短
        String desc = task.getDescription() != null ? task.getDescription() : "";
        if (desc.length() < 20) {
            score += 15;
            reasons.add("描述过短，需求不明确");
        }

        // 3. 截止日期紧迫
        if (task.getDueDate() != null && !"done".equals(task.getStatus())) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), task.getDueDate());
            if (daysLeft < 0) {
                score += 30;
                reasons.add("已超过截止日期");
            } else if (daysLeft == 0) {
                score += 30;
                reasons.add("截止日期不足24小时");
            } else if (daysLeft <= 3) {
                score += 15;
                reasons.add("截止日期不足3天");
            }
        }

        // 4. 依赖链复杂度
        if (task.getId() != null) {
            Long depCount = dependencyMapper.selectCount(
                new LambdaQueryWrapper<TaskDependency>()
                    .eq(TaskDependency::getTaskId, task.getId()));
            if (depCount > 5) {
                score += 20;
                reasons.add("依赖任务超过5个");
            } else if (depCount > 3) {
                score += 10;
                reasons.add("依赖任务超过3个");
            }
        }

        // 5. 指派人负载
        if (task.getAssigneeId() != null && task.getProjectId() != null) {
            long workload = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                    .eq(Task::getAssigneeId, task.getAssigneeId())
                    .eq(Task::getProjectId, task.getProjectId())
                    .eq(Task::getIsDeleted, false)
                    .in(Task::getStatus, "todo", "in_progress"));
            if (workload > 8) {
                score += 20;
                reasons.add("指派人负载过高（>8个活跃任务）");
            } else if (workload > 5) {
                score += 10;
                reasons.add("指派人负载较高（>5个活跃任务）");
            }
        }

        // 综合判断
        String level;
        if (score >= 40) {
            level = "high";
        } else if (score >= 20) {
            level = "medium";
        } else {
            level = "low";
        }

        String reason = reasons.isEmpty() ? "未发现明显风险" : String.join("; ", reasons);

        task.setRiskLevel(level);
        task.setRiskReason(reason);
        taskMapper.updateById(task);
    }

    /**
     * 评估项目下所有活跃任务的风险。
     */
    public void assessProject(Long projectId) {
        List<Task> activeTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false)
                .ne(Task::getStatus, "done")
                .isNull(Task::getParentId));
        for (Task t : activeTasks) {
            try {
                assess(t);
            } catch (Exception e) {
                log.error("Risk assessment failed for task {}", t.getId(), e);
            }
        }
    }

    /**
     * 获取项目的风险概览，按风险等级分组。
     */
    public Map<String, List<Map<String, Object>>> getProjectRisks(Long projectId) {
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false)
                .ne(Task::getStatus, "done")
                .isNull(Task::getParentId)
                .isNotNull(Task::getRiskLevel));

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        grouped.put("high", new ArrayList<>());
        grouped.put("medium", new ArrayList<>());
        grouped.put("low", new ArrayList<>());

        for (Task t : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", t.getId());
            item.put("key", t.getKey());
            item.put("title", t.getTitle());
            item.put("status", t.getStatus());
            item.put("priority", t.getPriority());
            item.put("riskLevel", t.getRiskLevel());
            item.put("riskReason", t.getRiskReason());
            item.put("assigneeId", t.getAssigneeId());
            item.put("dueDate", t.getDueDate());
            grouped.getOrDefault(t.getRiskLevel(), grouped.get("low")).add(item);
        }
        return grouped;
    }
}

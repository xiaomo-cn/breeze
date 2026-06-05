package cn.xiaomo.breeze.sprint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.sprint.dto.BurndownPoint;
import cn.xiaomo.breeze.sprint.dto.CreateSprintRequest;
import cn.xiaomo.breeze.sprint.dto.SprintDTO;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final TaskMapper taskMapper;
    private final ActivityLogger activityLogger;

    public SprintDTO create(Long projectId, CreateSprintRequest request) {
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setStatus("planning");
        sprint.setSortOrder(0);
        sprint.setCreatedAt(LocalDateTime.now());
        sprint.setUpdatedAt(LocalDateTime.now());
        sprintMapper.insert(sprint);

        activityLogger.log(projectId, getCurrentUserId(), "created", "sprint", sprint.getId(),
            Map.of("name", sprint.getName()));

        return toDTO(sprint);
    }

    public List<SprintDTO> listByProject(Long projectId) {
        List<Sprint> sprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .orderByDesc(Sprint::getCreatedAt));

        Set<Long> sprintIds = sprints.stream().map(Sprint::getId).collect(Collectors.toSet());
        Map<Long, List<Task>> tasksBySprint = new HashMap<>();
        if (!sprintIds.isEmpty()) {
            List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                    .in(Task::getSprintId, sprintIds)
                    .eq(Task::getIsDeleted, false));
            for (Task t : tasks) {
                tasksBySprint.computeIfAbsent(t.getSprintId(), k -> new ArrayList<>()).add(t);
            }
        }

        return sprints.stream().map(s -> {
            SprintDTO dto = toDTO(s);
            List<Task> sprintTasks = tasksBySprint.getOrDefault(s.getId(), Collections.emptyList());
            dto.setTaskCount(sprintTasks.size());
            dto.setCompletedTaskCount((int) sprintTasks.stream()
                .filter(t -> "done".equals(t.getStatus())).count());
            return dto;
        }).collect(Collectors.toList());
    }

    public SprintDTO getById(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");

        List<Task> sprintTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, sprintId)
                .eq(Task::getIsDeleted, false));

        SprintDTO dto = toDTO(sprint);
        dto.setTaskCount(sprintTasks.size());
        dto.setCompletedTaskCount((int) sprintTasks.stream()
            .filter(t -> "done".equals(t.getStatus())).count());
        return dto;
    }

    public SprintDTO update(Long sprintId, CreateSprintRequest request) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");
        if (request.getName() != null) sprint.setName(request.getName());
        if (request.getGoal() != null) sprint.setGoal(request.getGoal());
        if (request.getStartDate() != null) sprint.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) sprint.setEndDate(request.getEndDate());
        sprint.setUpdatedAt(LocalDateTime.now());
        sprintMapper.updateById(sprint);
        return toDTO(sprint);
    }

    public SprintDTO start(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");
        if (!"planning".equals(sprint.getStatus())) {
            throw new IllegalArgumentException("Only planning sprints can be started");
        }
        sprint.setStatus("active");
        sprint.setUpdatedAt(LocalDateTime.now());
        sprintMapper.updateById(sprint);

        activityLogger.log(sprint.getProjectId(), getCurrentUserId(), "started", "sprint", sprintId,
            Map.of("name", sprint.getName()));

        return toDTO(sprint);
    }

    @Transactional(rollbackFor = Exception.class)
    public SprintDTO close(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");
        if (!"active".equals(sprint.getStatus())) {
            throw new IllegalArgumentException("Only active sprints can be closed");
        }
        // Move unfinished tasks to backlog (sprintId = null)
        List<Task> sprintTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, sprintId)
                .eq(Task::getIsDeleted, false));
        for (Task t : sprintTasks) {
            if (!"done".equals(t.getStatus())) {
                t.setSprintId(null);
                t.setStatus("todo");
                taskMapper.updateById(t);
            }
        }
        sprint.setStatus("closed");
        sprint.setUpdatedAt(LocalDateTime.now());
        sprintMapper.updateById(sprint);

        activityLogger.log(sprint.getProjectId(), getCurrentUserId(), "closed", "sprint", sprintId,
            Map.of("name", sprint.getName()));

        return toDTO(sprint);
    }

    public List<BurndownPoint> burndown(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");

        List<Task> sprintTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, sprintId)
                .eq(Task::getIsDeleted, false));

        int totalPoints = sprintTasks.stream()
            .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0)
            .sum();

        LocalDate start = sprint.getStartDate();
        LocalDate end = sprint.getEndDate();
        if (start == null || end == null) return Collections.emptyList();

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (totalDays <= 0) return Collections.emptyList();

        List<BurndownPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (long day = 0; day < totalDays; day++) {
            LocalDate date = start.plusDays(day);
            int idealRemaining = (int) Math.round(totalPoints * (1.0 - (double) day / (totalDays - 1)));
            if (totalDays == 1) idealRemaining = 0;

            int actualRemaining = totalPoints;
            if (!date.isAfter(today)) {
                for (Task t : sprintTasks) {
                    int sp = t.getStoryPoints() != null ? t.getStoryPoints() : 0;
                    if ("done".equals(t.getStatus()) && t.getResolvedAt() != null
                        && !t.getResolvedAt().toLocalDate().isAfter(date)) {
                        actualRemaining -= sp;
                    }
                }
            } else {
                actualRemaining = -1; // future, no actual data
            }

            points.add(new BurndownPoint(date.toString(), idealRemaining,
                Math.max(0, actualRemaining)));
        }
        return points;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) return;

        // Unlink tasks
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getSprintId, sprintId));
        for (Task t : tasks) {
            t.setSprintId(null);
            taskMapper.updateById(t);
        }

        activityLogger.log(sprint.getProjectId(), getCurrentUserId(), "deleted", "sprint", sprintId,
            Map.of("name", sprint.getName()));

        sprintMapper.deleteById(sprintId);
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addTask(Long sprintId, Long taskId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");
        if (!"planning".equals(sprint.getStatus()) && !"active".equals(sprint.getStatus())) {
            throw new IllegalArgumentException("只能向规划中或进行中的 Sprint 添加任务");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");
        if (!task.getProjectId().equals(sprint.getProjectId())) {
            throw new IllegalArgumentException("任务不属于此项目");
        }
        if (task.getSprintId() != null && task.getSprintId().equals(sprintId)) {
            throw new IllegalArgumentException("任务已在此 Sprint 中");
        }

        task.setSprintId(sprintId);
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeTask(Long sprintId, Long taskId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");

        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");
        if (task.getSprintId() == null || !task.getSprintId().equals(sprintId)) {
            throw new IllegalArgumentException("任务不在此 Sprint 中");
        }

        task.setSprintId(null);
        task.setStatus("todo");
        taskMapper.updateById(task);
    }

    private SprintDTO toDTO(Sprint s) {
        SprintDTO dto = new SprintDTO();
        dto.setId(s.getId());
        dto.setProjectId(s.getProjectId());
        dto.setName(s.getName());
        dto.setGoal(s.getGoal());
        dto.setStartDate(s.getStartDate());
        dto.setEndDate(s.getEndDate());
        dto.setStatus(s.getStatus());
        dto.setSortOrder(s.getSortOrder());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}

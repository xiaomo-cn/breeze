package cn.xiaomo.breeze.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.event.SseEmitterRegistry;
import cn.xiaomo.breeze.notification.Notification;
import cn.xiaomo.breeze.notification.NotificationMapper;
import cn.xiaomo.breeze.notification.NotificationPayload;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskCollaboratorMapper taskCollaboratorMapper;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ProjectMemberMapper projectMemberMapper;
    private final ActivityLogger activityLogger;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(rollbackFor = Exception.class)
    public Task create(Long projectId, Task task, Long reporterId) {
        // 禁止在子任务下再创建子任务（只允许一级嵌套）
        if (task.getParentId() != null) {
            Task parent = getById(task.getParentId());
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("不能在子任务下再创建子任务，只支持一级嵌套");
            }
        }

        task.setProjectId(projectId);
        task.setReporterId(reporterId);
        if (task.getStatus() == null) {
            task.setStatus("todo");
        }
        if (task.getPriority() == null) {
            task.setPriority("medium");
        }
        if (task.getType() == null) {
            task.setType("task");
        }

        task.setKey(generateTaskKey(projectId));
        taskMapper.insert(task);
        eventPublisher.publishEvent(new TaskEventListener.TaskChangedEvent(task));
        broadcastTaskEvent(task.getProjectId(), "task_updated", task);
        activityLogger.log(projectId, reporterId, "created", "task", task.getId(),
            java.util.Map.of("title", task.getTitle()));

        // 创建者不需要给自己发通知，只通知被分配人
        if (task.getAssigneeId() != null && !task.getAssigneeId().equals(reporterId)) {
            User assigner = userMapper.selectById(reporterId);
            String assignerName = assigner != null && assigner.getDisplayName() != null
                ? assigner.getDisplayName() : (assigner != null ? assigner.getUsername() : "Someone");

            Notification notif = new Notification();
            notif.setUserId(task.getAssigneeId());
            notif.setType("TASK_ASSIGNED");
            notif.setTitle(assignerName + " 将任务 " + task.getKey() + " 分配给了你");
            notif.setBody(task.getTitle());
            notif.setReferenceType("task");
            notif.setReferenceId(task.getId());
            notif.setIsRead(false);
            notif.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notif);
            sendNotificationSse(task.getAssigneeId(), notif);
        }

        // 保存协作人
        if (task.getCollaboratorIds() != null && !task.getCollaboratorIds().isEmpty()) {
            saveCollaborators(task.getId(), task.getCollaboratorIds(), task.getAssigneeId());
            notifyCollaborators(task, reporterId, task.getCollaboratorIds());
        }

        return task;
    }

    /** 保存协作人列表（自动过滤掉负责人） */
    private void saveCollaborators(Long taskId, List<Long> collaboratorIds, Long assigneeId) {
        // 过滤掉负责人（不能同时是协作人）
        List<Long> filtered = collaboratorIds.stream()
            .filter(id -> !id.equals(assigneeId))
            .distinct()
            .toList();
        // 先删除旧的
        taskCollaboratorMapper.delete(new LambdaQueryWrapper<TaskCollaborator>()
            .eq(TaskCollaborator::getTaskId, taskId));
        // 再批量插入
        for (Long userId : filtered) {
            TaskCollaborator tc = new TaskCollaborator();
            tc.setTaskId(taskId);
            tc.setUserId(userId);
            tc.setCreatedAt(LocalDateTime.now());
            taskCollaboratorMapper.insert(tc);
        }
    }

    /** 查询任务的协作人 ID 列表 */
    public List<Long> getCollaboratorIds(Long taskId) {
        return taskCollaboratorMapper.selectList(
                new LambdaQueryWrapper<TaskCollaborator>()
                    .eq(TaskCollaborator::getTaskId, taskId))
            .stream()
            .map(TaskCollaborator::getUserId)
            .toList();
    }

    /** 通知协作人 */
    private void notifyCollaborators(Task task, Long reporterId, List<Long> collaboratorIds) {
        User assigner = userMapper.selectById(reporterId);
        String assignerName = assigner != null && assigner.getDisplayName() != null
            ? assigner.getDisplayName() : (assigner != null ? assigner.getUsername() : "Someone");

        for (Long userId : collaboratorIds) {
            // 不通知创建者自己，也不通知负责人（负责人已有 TASK_ASSIGNED 通知）
            if (userId.equals(reporterId) || userId.equals(task.getAssigneeId())) {
                continue;
            }
            Notification notif = new Notification();
            notif.setUserId(userId);
            notif.setType("COLLABORATOR_ADDED");
            notif.setTitle(assignerName + " 将你添加为任务 " + task.getKey() + " 的协作人");
            notif.setBody(task.getTitle());
            notif.setReferenceType("task");
            notif.setReferenceId(task.getId());
            notif.setIsRead(false);
            notif.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notif);
            sendNotificationSse(userId, notif);
        }
    }

    public PageDTO<Task> listByProject(Long projectId, String q, String status, String priority,
                                        String type, Long assigneeId, Long sprintId,
                                        int page, int size, String sortBy, String sortDir) {
        return listByProject(projectId, q, status, priority, type, assigneeId, sprintId, null, false, page, size, sortBy, sortDir);
    }

    /**
     * 带 parentId / topLevelOnly 过滤的任务列表查询。
     *
     * @param topLevelOnly 仅返回顶层任务（parent_id IS NULL）
     * @param parentId     指定父任务 ID，只返回其子任务（与 topLevelOnly 互斥，parentId 优先）
     */
    public PageDTO<Task> listByProject(Long projectId, String q, String status, String priority,
                                        String type, Long assigneeId, Long sprintId,
                                        Long parentId, boolean topLevelOnly,
                                        int page, int size, String sortBy, String sortDir) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
            .eq(Task::getProjectId, projectId)
            .eq(Task::getIsDeleted, false);

        if (parentId != null) {
            wrapper.eq(Task::getParentId, parentId);
        } else if (topLevelOnly) {
            wrapper.isNull(Task::getParentId);
        }

        if (q != null && !q.isBlank()) {
            wrapper.and(w -> w.like(Task::getTitle, q).or().like(Task::getDescription, q));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Task::getStatus, status);
        }
        if (priority != null && !priority.isBlank()) {
            wrapper.eq(Task::getPriority, priority);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(Task::getType, type);
        }
        if (assigneeId != null) {
            wrapper.eq(Task::getAssigneeId, assigneeId);
        }
        if (sprintId != null) {
            wrapper.eq(Task::getSprintId, sprintId);
        }

        // 排序
        boolean asc = !"desc".equalsIgnoreCase(sortDir);
        switch (sortBy != null ? sortBy : "") {
            case "priority" -> wrapper.orderBy(true, asc, Task::getPriority);
            case "dueDate" -> wrapper.orderBy(true, asc, Task::getDueDate);
            case "createdAt" -> wrapper.orderBy(true, asc, Task::getCreatedAt);
            default -> wrapper.orderByAsc(Task::getSortOrder);
        }

        IPage<Task> result = taskMapper.selectPage(Page.of(page, size), wrapper);
        // 批量加载协作人 ID
        batchLoadCollaboratorIds(result.getRecords());
        return PageDTO.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 批量加载任务的协作人 ID 列表 */
    private void batchLoadCollaboratorIds(List<Task> tasks) {
        if (tasks.isEmpty()) return;
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskCollaborator> allTCs = taskCollaboratorMapper.selectList(
            new LambdaQueryWrapper<TaskCollaborator>().in(TaskCollaborator::getTaskId, taskIds));
        Map<Long, List<Long>> tcMap = allTCs.stream()
            .collect(Collectors.groupingBy(TaskCollaborator::getTaskId,
                Collectors.mapping(TaskCollaborator::getUserId, Collectors.toList())));
        tasks.forEach(t -> t.setCollaboratorIds(tcMap.getOrDefault(t.getId(), List.of())));
    }

    public Task getById(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getIsDeleted())) {
            throw new IllegalArgumentException("Task not found");
        }
        task.setCollaboratorIds(getCollaboratorIds(taskId));
        return task;
    }

    /** 获取指定任务的所有未删除子任务（仅直接子任务，不含嵌套） */
    public List<Task> getChildren(Long parentId) {
        return taskMapper.selectByParentId(parentId);
    }

    /** 批量获取多个父任务的子任务统计 */
    public List<Map<String, Object>> getSubtaskStats(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.countByParentIds(parentIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public Task update(Long taskId, Task updates) {
        Task task = getById(taskId);

        // 在应用更新前检测负责人变更
        Long oldAssigneeId = task.getAssigneeId();

        if (updates.getTitle() != null) task.setTitle(updates.getTitle());
        if (updates.getDescription() != null) task.setDescription(updates.getDescription());
        if (updates.getStatus() != null) task.setStatus(updates.getStatus());
        if (updates.getPriority() != null) task.setPriority(updates.getPriority());
        if (updates.getType() != null) task.setType(updates.getType());
        if (updates.getAssigneeId() != null) task.setAssigneeId(updates.getAssigneeId());
        if (updates.getSprintId() != null) task.setSprintId(updates.getSprintId());
        if (updates.getDueDate() != null) task.setDueDate(updates.getDueDate());
        if (updates.getStoryPoints() != null) task.setStoryPoints(updates.getStoryPoints());
        if (updates.getSortOrder() != null) task.setSortOrder(updates.getSortOrder());
        if (updates.getKanbanColumnId() != null) task.setKanbanColumnId(updates.getKanbanColumnId());
        taskMapper.updateById(task);

        // 负责人变更时通知新负责人
        Long newAssigneeId = task.getAssigneeId();
        if (newAssigneeId != null && !newAssigneeId.equals(oldAssigneeId)) {
            User assigner = userMapper.selectById(task.getReporterId());
            String assignerName = assigner != null && assigner.getDisplayName() != null
                ? assigner.getDisplayName() : (assigner != null ? assigner.getUsername() : "Someone");

            Notification notif = new Notification();
            notif.setUserId(newAssigneeId);
            notif.setType("TASK_ASSIGNED");
            notif.setTitle(assignerName + " 将任务 " + task.getKey() + " 分配给了你");
            notif.setBody(task.getTitle());
            notif.setReferenceType("task");
            notif.setReferenceId(task.getId());
            notif.setIsRead(false);
            notif.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notif);
            sendNotificationSse(newAssigneeId, notif);
        }

        eventPublisher.publishEvent(new TaskEventListener.TaskChangedEvent(task));
        broadcastTaskEvent(task.getProjectId(), "task_updated", task);
        activityLogger.log(task.getProjectId(), task.getReporterId(), "updated", "task", task.getId(), null);

        // 协作人变更
        if (task.getCollaboratorIds() != null) {
            List<Long> oldCollaboratorIds = getCollaboratorIds(taskId);
            saveCollaborators(taskId, task.getCollaboratorIds(), task.getAssigneeId());
            // 只通知新增的协作人
            List<Long> newIds = new ArrayList<>(task.getCollaboratorIds().stream()
                .filter(id -> !id.equals(task.getAssigneeId())).toList());
            newIds.removeAll(oldCollaboratorIds);
            if (!newIds.isEmpty()) {
                notifyCollaborators(task, task.getReporterId(), newIds);
            }
        }

        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task updateStatus(Long taskId, String status, Integer sortOrder, Long kanbanColumnId) {
        Task task = getById(taskId);
        task.setStatus(status);
        if (sortOrder != null) {
            task.setSortOrder(sortOrder);
        }
        if (kanbanColumnId != null) {
            task.setKanbanColumnId(kanbanColumnId);
        }
        taskMapper.updateById(task);
        broadcastTaskEvent(task.getProjectId(), "task_updated", task);
        activityLogger.log(task.getProjectId(), null, "status_changed", "task", task.getId(),
            java.util.Map.of("status", task.getStatus()));
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long taskId) {
        Task task = getById(taskId);
        // 级联软删除所有子任务（递归处理多层嵌套）
        cascadeSoftDelete(taskId);
        taskMapper.deleteById(taskId);
        broadcastTaskEvent(task.getProjectId(), "task_deleted", task);
        activityLogger.log(task.getProjectId(), null, "deleted", "task", taskId, null);
    }

    /** 递归软删除所有子任务 */
    private void cascadeSoftDelete(Long parentId) {
        List<Task> children = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getParentId, parentId)
                .eq(Task::getIsDeleted, false));
        for (Task child : children) {
            cascadeSoftDelete(child.getId());
            taskMapper.deleteById(child.getId());
        }
    }

    @SneakyThrows
    private void broadcastTaskEvent(Long projectId, String eventName, Task task) {
        List<Long> memberIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, projectId))
            .stream()
            .map(ProjectMember::getUserId)
            .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", task.getId());
        data.put("key", task.getKey());
        data.put("title", task.getTitle());
        data.put("status", task.getStatus());
        data.put("priority", task.getPriority());
        data.put("type", task.getType());
        data.put("assigneeId", task.getAssigneeId());
        data.put("sprintId", task.getSprintId());
        data.put("sortOrder", task.getSortOrder() != null ? task.getSortOrder() : 0);
        data.put("projectId", task.getProjectId());
        data.put("parentId", task.getParentId());
        data.put("collaboratorIds", task.getCollaboratorIds());
        sseEmitterRegistry.broadcast(memberIds, eventName, objectMapper.writeValueAsString(data));
    }

    @SneakyThrows
    private void sendNotificationSse(Long userId, Notification notif) {
        sseEmitterRegistry.send(userId, "notification", toJson(NotificationPayload.from(notif)));
    }

    @SneakyThrows
    private static String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

    private String generateTaskKey(Long projectId) {
        String counterKey = "task:counter:" + projectId;
        Long seq = redisTemplate.opsForValue().increment(counterKey);
        return "T-" + seq;
    }

    /**
     * 获取当前用户的逾期/即将到期任务，按紧迫度分组。
     */
    public OverdueTasks getMyOverdueTasks(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(3);

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, userId)
                .eq(Task::getIsDeleted, false)
                .notIn(Task::getStatus, "done")
                .isNotNull(Task::getDueDate)
                .le(Task::getDueDate, soon)
                .orderByAsc(Task::getDueDate));

        List<TaskSummary> overdue = new ArrayList<>();
        List<TaskSummary> dueToday = new ArrayList<>();
        List<TaskSummary> dueSoon = new ArrayList<>();

        for (Task t : tasks) {
            TaskSummary s = toSummary(t);
            if (t.getDueDate().isBefore(today)) {
                overdue.add(s);
            } else if (t.getDueDate().isEqual(today)) {
                dueToday.add(s);
            } else {
                dueSoon.add(s);
            }
        }

        return new OverdueTasks(overdue, dueToday, dueSoon);
    }

    private TaskSummary toSummary(Task t) {
        return new TaskSummary(t.getId(), t.getKey(), t.getTitle(),
            t.getStatus(), t.getPriority(), t.getDueDate(), t.getProjectId());
    }

    public record OverdueTasks(List<TaskSummary> overdue, List<TaskSummary> dueToday, List<TaskSummary> dueSoon) {}

    public record TaskSummary(Long id, String key, String title, String status, String priority,
                              java.time.LocalDate dueDate, Long projectId) {}
}

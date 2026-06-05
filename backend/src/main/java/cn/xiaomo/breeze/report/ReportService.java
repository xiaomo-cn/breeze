package cn.xiaomo.breeze.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.report.dto.DailyReportDTO;
import cn.xiaomo.breeze.report.dto.SprintReportDTO;
import cn.xiaomo.breeze.report.dto.SprintReportDTO.BurndownPoint;
import cn.xiaomo.breeze.report.dto.TaskSummary;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO.DailyPoint;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO.MemberContribution;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TaskMapper taskMapper;
    private final SprintMapper sprintMapper;
    private final UserMapper userMapper;

    public DailyReportDTO dailyReport(Long projectId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Task> allTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false));

        List<Task> completed = allTasks.stream()
            .filter(t -> "done".equals(t.getStatus())
                && t.getResolvedAt() != null
                && !t.getResolvedAt().isBefore(dayStart)
                && t.getResolvedAt().isBefore(dayEnd))
            .collect(Collectors.toList());

        List<Task> inProgress = allTasks.stream()
            .filter(t -> "in_progress".equals(t.getStatus()))
            .collect(Collectors.toList());

        List<Task> blocked = allTasks.stream()
            .filter(t -> "blocked".equals(t.getStatus()))
            .collect(Collectors.toList());

        int createdCount = (int) allTasks.stream()
            .filter(t -> t.getCreatedAt() != null
                && !t.getCreatedAt().isBefore(dayStart)
                && t.getCreatedAt().isBefore(dayEnd))
            .count();

        Map<Long, String> userNames = loadUserNames(allTasks);

        DailyReportDTO dto = new DailyReportDTO();
        dto.setDate(date);
        dto.setCompletedTasks(toSummaries(completed, userNames));
        dto.setInProgressTasks(toSummaries(inProgress, userNames));
        dto.setBlockedTasks(toSummaries(blocked, userNames));
        dto.setCreatedCount(createdCount);
        dto.setCompletedCount(completed.size());
        return dto;
    }

    public WeeklyReportDTO weeklyReport(Long projectId, LocalDate start, LocalDate end) {
        List<Task> allTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false));

        Map<Long, String> userNames = loadUserNames(allTasks);

        List<DailyPoint> dailyPoints = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end) && !d.isAfter(LocalDate.now());
             d = d.plusDays(1)) {
            LocalDateTime ds = d.atStartOfDay();
            LocalDateTime de = d.plusDays(1).atStartOfDay();
            int created = (int) allTasks.stream()
                .filter(t -> t.getCreatedAt() != null
                    && !t.getCreatedAt().isBefore(ds) && t.getCreatedAt().isBefore(de))
                .count();
            int completed = (int) allTasks.stream()
                .filter(t -> "done".equals(t.getStatus())
                    && t.getResolvedAt() != null
                    && !t.getResolvedAt().isBefore(ds) && t.getResolvedAt().isBefore(de))
                .count();
            DailyPoint dp = new DailyPoint();
            dp.setDate(d);
            dp.setCreated(created);
            dp.setCompleted(completed);
            dailyPoints.add(dp);
        }

        Map<String, Integer> distribution = new HashMap<>();
        for (Task t : allTasks) {
            distribution.merge(t.getStatus(), 1, Integer::sum);
        }

        Map<Long, int[]> memberStats = new HashMap<>();
        for (Task t : allTasks) {
            if (t.getAssigneeId() != null) {
                int[] stats = memberStats.computeIfAbsent(t.getAssigneeId(), k -> new int[2]);
                if ("done".equals(t.getStatus())) stats[0]++;
                stats[1]++;
            }
        }
        List<MemberContribution> contributions = memberStats.entrySet().stream()
            .map(e -> {
                MemberContribution mc = new MemberContribution();
                mc.setUserName(userNames.getOrDefault(e.getKey(), "未知用户"));
                mc.setCompleted(e.getValue()[0]);
                mc.setCreated(e.getValue()[1]);
                return mc;
            })
            .collect(Collectors.toList());

        int newTasks = (int) allTasks.stream()
            .filter(t -> t.getCreatedAt() != null
                && !t.getCreatedAt().isBefore(start.atStartOfDay())
                && t.getCreatedAt().isBefore(end.plusDays(1).atStartOfDay()))
            .count();
        int completedTasks = (int) allTasks.stream()
            .filter(t -> "done".equals(t.getStatus())
                && t.getResolvedAt() != null
                && !t.getResolvedAt().isBefore(start.atStartOfDay())
                && t.getResolvedAt().isBefore(end.plusDays(1).atStartOfDay()))
            .count();
        long remaining = allTasks.stream()
            .filter(t -> !"done".equals(t.getStatus())).count();

        WeeklyReportDTO dto = new WeeklyReportDTO();
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setDailyPoints(dailyPoints);
        dto.setTaskDistribution(distribution);
        dto.setContributions(contributions);
        dto.setNewTasks(newTasks);
        dto.setCompletedTasks(completedTasks);
        dto.setRemainingTasks((int) remaining);
        return dto;
    }

    public SprintReportDTO sprintReport(Long projectId, Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) throw new IllegalArgumentException("Sprint not found");

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, sprintId)
                .eq(Task::getIsDeleted, false));

        Map<Long, String> userNames = loadUserNames(tasks);

        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> "done".equals(t.getStatus())).count();
        int totalSP = tasks.stream().mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
        int completedSP = tasks.stream()
            .filter(t -> "done".equals(t.getStatus()))
            .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
        double rate = total > 0 ? (double) completed / total : 0;

        Map<Long, int[]> memberStats = new HashMap<>();
        for (Task t : tasks) {
            if (t.getAssigneeId() != null) {
                int[] stats = memberStats.computeIfAbsent(t.getAssigneeId(), k -> new int[2]);
                if ("done".equals(t.getStatus())) stats[0]++;
                stats[1]++;
            }
        }
        List<MemberContribution> contributions = memberStats.entrySet().stream()
            .map(e -> {
                MemberContribution mc = new MemberContribution();
                mc.setUserName(userNames.getOrDefault(e.getKey(), "未知用户"));
                mc.setCompleted(e.getValue()[0]);
                mc.setCreated(e.getValue()[1]);
                return mc;
            })
            .collect(Collectors.toList());

        List<BurndownPoint> burndown = computeBurndown(sprint, tasks);

        SprintReportDTO dto = new SprintReportDTO();
        dto.setSprintId(sprintId);
        dto.setSprintName(sprint.getName());
        dto.setSprintGoal(sprint.getGoal());
        dto.setSprintStatus(sprint.getStatus());
        dto.setTotalTasks(total);
        dto.setCompletedTasks(completed);
        dto.setTotalStoryPoints(totalSP);
        dto.setCompletedStoryPoints(completedSP);
        dto.setCompletionRate(rate);
        dto.setContributions(contributions);
        dto.setBurndown(burndown);
        return dto;
    }

    private List<BurndownPoint> computeBurndown(Sprint sprint, List<Task> tasks) {
        LocalDate start = sprint.getStartDate();
        LocalDate end = sprint.getEndDate();
        if (start == null || end == null) return Collections.emptyList();

        int totalPoints = tasks.stream()
            .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
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
                for (Task t : tasks) {
                    int sp = t.getStoryPoints() != null ? t.getStoryPoints() : 0;
                    if ("done".equals(t.getStatus()) && t.getResolvedAt() != null
                        && !t.getResolvedAt().toLocalDate().isAfter(date)) {
                        actualRemaining -= sp;
                    }
                }
            } else {
                actualRemaining = -1;
            }

            BurndownPoint bp = new BurndownPoint();
            bp.setDate(date.toString());
            bp.setIdealRemaining(idealRemaining);
            bp.setActualRemaining(Math.max(0, actualRemaining));
            points.add(bp);
        }
        return points;
    }

    private List<TaskSummary> toSummaries(List<Task> tasks, Map<Long, String> userNames) {
        return tasks.stream().map(t -> {
            TaskSummary s = new TaskSummary();
            s.setId(t.getId());
            s.setKey(t.getKey());
            s.setTitle(t.getTitle());
            s.setStatus(t.getStatus());
            s.setPriority(t.getPriority());
            s.setAssigneeName(userNames.getOrDefault(t.getAssigneeId(), null));
            return s;
        }).collect(Collectors.toList());
    }

    private Map<Long, String> loadUserNames(List<Task> tasks) {
        Set<Long> userIds = tasks.stream()
            .map(Task::getAssigneeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> map = new HashMap<>();
        for (User u : users) {
            map.put(u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
        }
        return map;
    }
}

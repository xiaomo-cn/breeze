package cn.xiaomo.breeze.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.report.dto.DailyReportDTO;
import cn.xiaomo.breeze.report.dto.SprintReportDTO;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.sprint.Sprint;
import cn.xiaomo.breeze.sprint.SprintMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ReportService reportService;

    private static final Long PROJECT_ID = 1L;

    private Task task(Long id, String status, String priority, Long assigneeId,
                      LocalDateTime resolvedAt, int storyPoints) {
        Task t = new Task();
        t.setId(id);
        t.setKey("T-" + id);
        t.setTitle("Task " + id);
        t.setProjectId(PROJECT_ID);
        t.setStatus(status);
        t.setPriority(priority);
        t.setAssigneeId(assigneeId);
        t.setResolvedAt(resolvedAt);
        t.setStoryPoints(storyPoints);
        t.setIsDeleted(false);
        t.setCreatedAt(LocalDateTime.now().minusDays(1));
        return t;
    }

    @Nested
    class DailyReport {

        @Test
        void shouldReturnDailyReport() {
            LocalDate today = LocalDate.now();
            Task done1 = task(1L, "done", "high", 10L, LocalDateTime.now(), 5);
            Task inProgress = task(2L, "in_progress", "medium", 20L, null, 3);
            Task blocked = task(3L, "blocked", "high", 10L, null, 8);

            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(done1, inProgress, blocked));

            DailyReportDTO report = reportService.dailyReport(PROJECT_ID, today);

            assertThat(report.getCompletedTasks()).hasSize(1);
            assertThat(report.getInProgressTasks()).hasSize(1);
            assertThat(report.getBlockedTasks()).hasSize(1);
            assertThat(report.getCompletedCount()).isEqualTo(1);
        }
    }

    @Nested
    class WeeklyReport {

        @Test
        void shouldReturnWeeklyReport() {
            LocalDate start = LocalDate.of(2025, 6, 1);
            LocalDate end = LocalDate.of(2025, 6, 7);
            Task t1 = task(1L, "done", "medium", 10L,
                LocalDateTime.of(2025, 6, 3, 10, 0), 5);

            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(t1));

            WeeklyReportDTO report = reportService.weeklyReport(PROJECT_ID, start, end);

            assertThat(report.getStartDate()).isEqualTo(start);
            assertThat(report.getEndDate()).isEqualTo(end);
            assertThat(report.getCompletedTasks()).isEqualTo(1);
        }
    }

    @Nested
    class SprintReport {

        @Test
        void shouldReturnSprintReport() {
            Long sprintId = 100L;
            Sprint sprint = new Sprint();
            sprint.setId(sprintId);
            sprint.setProjectId(PROJECT_ID);
            sprint.setName("Sprint 1");
            sprint.setGoal("Goal");
            sprint.setStatus("active");
            sprint.setStartDate(LocalDate.of(2025, 6, 1));
            sprint.setEndDate(LocalDate.of(2025, 6, 14));

            Task done = task(1L, "done", "high", 10L,
                LocalDateTime.of(2025, 6, 3, 10, 0), 5);
            Task todo = task(2L, "in_progress", "medium", 20L, null, 3);

            when(sprintMapper.selectById(sprintId)).thenReturn(sprint);
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(done, todo));

            SprintReportDTO report = reportService.sprintReport(PROJECT_ID, sprintId);

            assertThat(report.getSprintName()).isEqualTo("Sprint 1");
            assertThat(report.getTotalTasks()).isEqualTo(2);
            assertThat(report.getCompletedTasks()).isEqualTo(1);
            assertThat(report.getCompletedStoryPoints()).isEqualTo(5);
            assertThat(report.getTotalStoryPoints()).isEqualTo(8);
        }

        @Test
        void shouldThrowWhenSprintNotFound() {
            when(sprintMapper.selectById(99L)).thenReturn(null);

            try {
                reportService.sprintReport(PROJECT_ID, 99L);
                assertThat(true).isFalse(); // should not reach here
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).isEqualTo("Sprint not found");
            }
        }
    }
}

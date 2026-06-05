package cn.xiaomo.breeze.sprint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.sprint.dto.BurndownPoint;
import cn.xiaomo.breeze.sprint.dto.CreateSprintRequest;
import cn.xiaomo.breeze.sprint.dto.SprintDTO;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock
    private SprintMapper sprintMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ActivityLogger activityLogger;

    @InjectMocks
    private SprintService sprintService;

    private static final Long PROJECT_ID = 1L;
    private static final Long SPRINT_ID = 10L;

    private Sprint sprint() {
        Sprint s = new Sprint();
        s.setId(SPRINT_ID);
        s.setProjectId(PROJECT_ID);
        s.setName("Sprint 1");
        s.setGoal("Complete core features");
        s.setStartDate(LocalDate.of(2025, 1, 1));
        s.setEndDate(LocalDate.of(2025, 1, 14));
        s.setStatus("planning");
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private CreateSprintRequest request() {
        CreateSprintRequest req = new CreateSprintRequest();
        req.setName("Sprint 1");
        req.setGoal("Complete core features");
        req.setStartDate(LocalDate.of(2025, 1, 1));
        req.setEndDate(LocalDate.of(2025, 1, 14));
        return req;
    }

    @Nested
    class Create {

        @Test
        void shouldCreateSprint() {
            SprintDTO result = sprintService.create(PROJECT_ID, request());

            verify(sprintMapper).insert(any(Sprint.class));
            verify(activityLogger).log(eq(PROJECT_ID), isNull(), eq("created"), eq("sprint"), isNull(),
                anyMap());
            assertThat(result.getName()).isEqualTo("Sprint 1");
            assertThat(result.getGoal()).isEqualTo("Complete core features");
            assertThat(result.getStatus()).isEqualTo("planning");
        }
    }

    @Nested
    class ListByProject {

        @Test
        void shouldListSprintsWithTaskCounts() {
            Sprint s = sprint();
            Task t1 = task(1L, "todo", 5);
            Task t2 = task(2L, "done", 3);

            when(sprintMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s));
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(t1, t2));

            List<SprintDTO> result = sprintService.listByProject(PROJECT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTaskCount()).isEqualTo(2);
            assertThat(result.get(0).getCompletedTaskCount()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyList() {
            when(sprintMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            List<SprintDTO> result = sprintService.listByProject(PROJECT_ID);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateSprintFields() {
            Sprint existing = sprint();
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(existing);

            CreateSprintRequest req = new CreateSprintRequest();
            req.setName("Updated Sprint");
            req.setGoal("Updated goal");

            SprintDTO result = sprintService.update(SPRINT_ID, req);

            assertThat(result.getName()).isEqualTo("Updated Sprint");
            assertThat(result.getGoal()).isEqualTo("Updated goal");
            verify(sprintMapper).updateById(existing);
        }

        @Test
        void shouldOnlyUpdateNonNullFields() {
            Sprint existing = sprint();
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(existing);

            CreateSprintRequest req = new CreateSprintRequest();
            req.setName("New Name");

            SprintDTO result = sprintService.update(SPRINT_ID, req);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getGoal()).isEqualTo("Complete core features"); // unchanged
        }

        @Test
        void shouldThrowWhenSprintNotFound() {
            when(sprintMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> sprintService.update(99L, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sprint not found");
        }
    }

    @Nested
    class Start {

        @Test
        void shouldStartPlanningSprint() {
            Sprint existing = sprint();
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(existing);

            SprintDTO result = sprintService.start(SPRINT_ID);

            assertThat(result.getStatus()).isEqualTo("active");
            verify(sprintMapper).updateById(existing);
            verify(activityLogger).log(eq(PROJECT_ID), isNull(), eq("started"), eq("sprint"),
                eq(SPRINT_ID), anyMap());
        }

        @Test
        void shouldRejectStartNonPlanningSprint() {
            Sprint active = sprint();
            active.setStatus("active");
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(active);

            assertThatThrownBy(() -> sprintService.start(SPRINT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only planning sprints can be started");
        }
    }

    @Nested
    class Close {

        @Test
        void shouldCloseAndMoveUnfinishedTasksToBacklog() {
            Sprint existing = sprint();
            existing.setStatus("active");

            Task doneTask = task(1L, "done", 3);
            Task todoTask = task(2L, "todo", 5);

            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(existing);
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(doneTask, todoTask));

            SprintDTO result = sprintService.close(SPRINT_ID);

            assertThat(result.getStatus()).isEqualTo("closed");
            // unfinished task moved to backlog
            assertThat(todoTask.getSprintId()).isNull();
            assertThat(todoTask.getStatus()).isEqualTo("todo");
            verify(taskMapper).updateById(todoTask);
            // done task unchanged
            assertThat(doneTask.getSprintId()).isEqualTo(SPRINT_ID);
        }

        @Test
        void shouldRejectCloseNonActiveSprint() {
            Sprint planning = sprint();
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(planning);

            assertThatThrownBy(() -> sprintService.close(SPRINT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only active sprints can be closed");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldDeleteAndUnlinkTasks() {
            Sprint existing = sprint();
            Task t = task(1L, "todo", 3);

            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(existing);
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(t));

            sprintService.delete(SPRINT_ID);

            assertThat(t.getSprintId()).isNull();
            verify(taskMapper).updateById(t);
            verify(sprintMapper).deleteById(SPRINT_ID);
            verify(activityLogger).log(eq(PROJECT_ID), isNull(), eq("deleted"), eq("sprint"),
                eq(SPRINT_ID), anyMap());
        }

        @Test
        void shouldHandleNonExistentSprint() {
            when(sprintMapper.selectById(99L)).thenReturn(null);

            sprintService.delete(99L);

            verify(sprintMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    class Burndown {

        @Test
        void shouldCalculateBurndownPoints() {
            Sprint s = sprint();
            s.setStatus("active");
            Task completedTask = task(1L, "done", 8);
            completedTask.setResolvedAt(LocalDateTime.of(2025, 1, 3, 10, 0));
            Task remaining = task(2L, "in_progress", 5);

            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(s);
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(completedTask, remaining));

            List<BurndownPoint> points = sprintService.burndown(SPRINT_ID);

            // 14 days sprint => 14 points
            assertThat(points).hasSize(14);
            // Day 3: ideal ~7, actual = 13 - 8 = 5
            BurndownPoint day3 = points.get(2);
            assertThat(day3.getIdealRemaining()).isPositive();
            assertThat(day3.getActualRemaining()).isGreaterThanOrEqualTo(0);
            // Day 1 (start): ideal = 13, actual = 13 (or less if resolved same day)
            assertThat(points.get(0).getIdealRemaining()).isEqualTo(13);
        }

        @Test
        void shouldReturnEmptyForMissingDates() {
            Sprint s = sprint();
            s.setStartDate(null);
            when(sprintMapper.selectById(SPRINT_ID)).thenReturn(s);

            List<BurndownPoint> points = sprintService.burndown(SPRINT_ID);
            assertThat(points).isEmpty();
        }
    }

    private Task task(Long id, String status, int storyPoints) {
        Task t = new Task();
        t.setId(id);
        t.setSprintId(SPRINT_ID);
        t.setTitle("Task " + id);
        t.setStatus(status);
        t.setStoryPoints(storyPoints);
        t.setIsDeleted(false);
        return t;
    }
}

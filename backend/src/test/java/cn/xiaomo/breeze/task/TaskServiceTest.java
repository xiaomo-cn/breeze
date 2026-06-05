package cn.xiaomo.breeze.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.event.SseEmitterRegistry;
import cn.xiaomo.breeze.notification.Notification;
import cn.xiaomo.breeze.notification.NotificationMapper;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private ActivityLogger activityLogger;

    @InjectMocks
    private TaskService taskService;

    private static final Long PROJECT_ID = 1L;
    private static final Long TASK_ID = 100L;
    private static final Long REPORTER_ID = 10L;

    @Nested
    class Create {

        @Test
        void shouldCreateTaskWithDefaults() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("task:counter:" + PROJECT_ID)).thenReturn(42L);
            when(taskMapper.insert(any(Task.class))).thenAnswer(inv -> {
                Task t = inv.getArgument(0);
                t.setId(TASK_ID);
                return 1;
            });
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            Task task = new Task();
            task.setTitle("New Task");
            Task result = taskService.create(PROJECT_ID, task, REPORTER_ID);

            assertThat(result.getId()).isEqualTo(TASK_ID);
            assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(result.getReporterId()).isEqualTo(REPORTER_ID);
            assertThat(result.getStatus()).isEqualTo("todo");
            assertThat(result.getPriority()).isEqualTo("medium");
            assertThat(result.getType()).isEqualTo("task");
            assertThat(result.getKey()).isEqualTo("T-42");

            verify(eventPublisher).publishEvent(any(TaskEventListener.TaskChangedEvent.class));
        }

        @Test
        void shouldKeepExplicitlySetFields() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(any())).thenReturn(1L);
            when(taskMapper.insert(any(Task.class))).thenAnswer(inv -> {
                Task t = inv.getArgument(0);
                t.setId(TASK_ID);
                return 1;
            });
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            Task task = new Task();
            task.setTitle("Bug Report");
            task.setStatus("in_progress");
            task.setPriority("critical");
            task.setType("bug");

            Task result = taskService.create(PROJECT_ID, task, REPORTER_ID);

            assertThat(result.getStatus()).isEqualTo("in_progress");
            assertThat(result.getPriority()).isEqualTo("critical");
            assertThat(result.getType()).isEqualTo("bug");
        }
    }

    @Nested
    class ListByProject {

        @Test
        void shouldListTasks() {
            when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of(task())));

            PageDTO<Task> result = taskService.listByProject(PROJECT_ID,
                    null, null, null, null, null, null, 1, 20, null, null);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).hasSize(1);
        }

        @Test
        void shouldFilterByStatusAndPriority() {
            when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of()));

            taskService.listByProject(PROJECT_ID, "todo", "high", "bug", null, null, null, 1, 20, null, null);

            verify(taskMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        void shouldSortByDescendingPriority() {
            when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of()));

            taskService.listByProject(PROJECT_ID, null, null, null, null, null, null, 1, 20, "priority", "desc");

            verify(taskMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        void shouldDefaultSortByOrder() {
            when(taskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of()));

            taskService.listByProject(PROJECT_ID, null, null, null, null, null, null, 1, 20, null, "asc");

            verify(taskMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    class GetById {

        @Test
        void shouldReturnTask() {
            Task t = task();
            when(taskMapper.selectById(TASK_ID)).thenReturn(t);

            Task result = taskService.getById(TASK_ID);

            assertThat(result).isEqualTo(t);
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(taskMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> taskService.getById(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Task not found");
        }

        @Test
        void shouldThrowWhenSoftDeleted() {
            Task t = task();
            t.setIsDeleted(true);
            when(taskMapper.selectById(TASK_ID)).thenReturn(t);

            assertThatThrownBy(() -> taskService.getById(TASK_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Task not found");
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateFields() {
            Task existing = task();
            when(taskMapper.selectById(TASK_ID)).thenReturn(existing);
            when(taskMapper.updateById(any(Task.class))).thenReturn(1);
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            Task updates = new Task();
            updates.setTitle("Updated Title");
            updates.setPriority("low");
            updates.setDueDate(LocalDate.of(2026, 6, 1));
            updates.setStoryPoints(5);

            Task result = taskService.update(TASK_ID, updates);

            assertThat(result.getTitle()).isEqualTo("Updated Title");
            assertThat(result.getPriority()).isEqualTo("low");
            assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(result.getStoryPoints()).isEqualTo(5);

            verify(eventPublisher).publishEvent(any(TaskEventListener.TaskChangedEvent.class));
        }

        @Test
        void shouldThrowWhenTaskNotFound() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(null);

            assertThatThrownBy(() -> taskService.update(TASK_ID, new Task()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldUpdateStatusOnly() {
            Task existing = task();
            when(taskMapper.selectById(TASK_ID)).thenReturn(existing);
            when(taskMapper.updateById(any(Task.class))).thenReturn(1);
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            Task result = taskService.updateStatus(TASK_ID, "done", null, null);

            assertThat(result.getStatus()).isEqualTo("done");
            // 原有字段不变
            assertThat(result.getTitle()).isEqualTo("Test Task");
        }

        @Test
        void shouldUpdateStatusWithSortOrderAndColumn() {
            Task existing = task();
            when(taskMapper.selectById(TASK_ID)).thenReturn(existing);
            when(taskMapper.updateById(any(Task.class))).thenReturn(1);
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            Task result = taskService.updateStatus(TASK_ID, "in_progress", 3, 5L);

            assertThat(result.getStatus()).isEqualTo("in_progress");
            assertThat(result.getSortOrder()).isEqualTo(3);
            assertThat(result.getKanbanColumnId()).isEqualTo(5L);
        }
    }

    @Nested
    class SoftDelete {

        @Test
        void shouldDeleteTask() {
            Task t = task();
            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(taskMapper.deleteById(TASK_ID)).thenReturn(1);
            lenient().when(projectMemberMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

            taskService.softDelete(TASK_ID);

            verify(taskMapper).deleteById(TASK_ID);
        }

        @Test
        void shouldThrowWhenTaskNotFound() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(null);

            assertThatThrownBy(() -> taskService.softDelete(TASK_ID))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(taskMapper, never()).deleteById(ArgumentMatchers.<Long>any());
        }
    }

    // -- helpers --

    private Task task() {
        Task t = new Task();
        t.setId(TASK_ID);
        t.setProjectId(PROJECT_ID);
        t.setTitle("Test Task");
        t.setStatus("todo");
        t.setPriority("medium");
        t.setType("task");
        t.setKey("T-1");
        t.setReporterId(REPORTER_ID);
        t.setIsDeleted(false);
        return t;
    }

    private Page<Task> pageOf(List<Task> records) {
        Page<Task> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }
}

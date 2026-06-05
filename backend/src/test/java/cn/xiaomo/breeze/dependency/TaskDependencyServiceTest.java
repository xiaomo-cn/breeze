package cn.xiaomo.breeze.dependency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.dependency.dto.CreateDependencyRequest;
import cn.xiaomo.breeze.dependency.dto.DependencyDTO;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskDependencyServiceTest {

    @Mock private TaskDependencyMapper dependencyMapper;
    @Mock private TaskMapper taskMapper;

    @InjectMocks
    private TaskDependencyService dependencyService;

    private static final Long TASK_A = 1L;
    private static final Long TASK_B = 2L;
    private static final Long TASK_C = 3L;

    @Nested
    class Create {
        @Test
        void shouldCreateDependency() {
            Task taskA = new Task(); taskA.setId(TASK_A); taskA.setProjectId(1L);
            Task taskB = new Task(); taskB.setId(TASK_B); taskB.setProjectId(1L);

            when(taskMapper.selectById(TASK_A)).thenReturn(taskA);
            when(taskMapper.selectById(TASK_B)).thenReturn(taskB);
            when(dependencyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            CreateDependencyRequest req = new CreateDependencyRequest();
            req.setDependsOnTaskId(TASK_B);
            req.setType("blocks");

            DependencyDTO result = dependencyService.create(TASK_A, req);

            verify(dependencyMapper).insert(any(TaskDependency.class));
            assertThat(result.getType()).isEqualTo("blocks");
        }

        @Test
        void shouldDetectCircularBlocksDependency() {
            Task taskA = new Task(); taskA.setId(TASK_A); taskA.setProjectId(1L);
            Task taskB = new Task(); taskB.setId(TASK_B); taskB.setProjectId(1L);
            TaskDependency existing = new TaskDependency();
            existing.setTaskId(TASK_A); existing.setDependsOnTaskId(TASK_B); existing.setType("blocks");

            when(taskMapper.selectById(TASK_B)).thenReturn(taskB);
            when(taskMapper.selectById(TASK_A)).thenReturn(taskA);
            when(dependencyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));

            CreateDependencyRequest req = new CreateDependencyRequest();
            req.setDependsOnTaskId(TASK_A);
            req.setType("blocks");

            assertThatThrownBy(() -> dependencyService.create(TASK_B, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circular");
        }

        @Test
        void shouldAllowRelatesToWithoutCycleCheck() {
            Task taskA = new Task(); taskA.setId(TASK_A); taskA.setProjectId(1L);
            Task taskB = new Task(); taskB.setId(TASK_B); taskB.setProjectId(1L);

            when(taskMapper.selectById(TASK_A)).thenReturn(taskA);
            when(taskMapper.selectById(TASK_B)).thenReturn(taskB);

            CreateDependencyRequest req = new CreateDependencyRequest();
            req.setDependsOnTaskId(TASK_B);
            req.setType("relates_to");

            DependencyDTO result = dependencyService.create(TASK_A, req);
            assertThat(result.getType()).isEqualTo("relates_to");
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldDeleteDependency() {
            TaskDependency dep = new TaskDependency();
            dep.setId(10L); dep.setTaskId(TASK_A);
            when(dependencyMapper.selectById(10L)).thenReturn(dep);

            dependencyService.delete(TASK_A, 10L);

            verify(dependencyMapper).deleteById(10L);
        }
    }

    @Nested
    class ListByTask {
        @Test
        void shouldListDependenciesWithTaskInfo() {
            TaskDependency dep = new TaskDependency();
            dep.setId(10L); dep.setTaskId(TASK_A); dep.setDependsOnTaskId(TASK_B);
            dep.setType("blocks");

            Task taskB = new Task(); taskB.setId(TASK_B); taskB.setKey("T-2");
            taskB.setTitle("Task B");

            when(dependencyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(dep));
            when(taskMapper.selectBatchIds(anyCollection())).thenReturn(List.of(taskB));

            List<DependencyDTO> result = dependencyService.listByTask(TASK_A);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDependsOnTaskKey()).isEqualTo("T-2");
        }
    }
}

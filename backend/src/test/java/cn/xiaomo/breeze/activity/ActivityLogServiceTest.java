package cn.xiaomo.breeze.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogMapper activityLogMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;

    @InjectMocks
    private ActivityLogService activityLogService;

    private static final Long PROJECT_ID = 1L;
    private static final Long USER_ID = 10L;

    private ActivityLog log(Long id, String actionType, String entityType, Long userId) {
        ActivityLog log = new ActivityLog();
        log.setId(id);
        log.setProjectId(PROJECT_ID);
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(100L);
        log.setDetails(Map.of("title", "Test"));
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private User user(Long id, String displayName) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setDisplayName(displayName);
        return u;
    }

    @Nested
    class ListByProject {

        @Test
        void shouldReturnActivityWithUserInfo() {
            ActivityLog log = log(1L, "created", "task", USER_ID);
            User u = user(USER_ID, "Alice");

            when(activityLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(List.of(log)));
            when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(u));

            PageDTO<ActivityLogDTO> result = activityLogService.listByProject(PROJECT_ID, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).getActionType()).isEqualTo("created");
            assertThat(result.items().get(0).getDisplayName()).isEqualTo("Alice");
        }

        @Test
        void shouldReturnEmptyPage() {
            when(activityLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage());

            PageDTO<ActivityLogDTO> result = activityLogService.listByProject(PROJECT_ID, 1, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isZero();
        }

        @Test
        void shouldHandleNullDetails() {
            ActivityLog log = log(1L, "updated", "task", USER_ID);
            log.setDetails(null);

            when(activityLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(List.of(log)));
            when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(user(USER_ID, "Bob")));

            PageDTO<ActivityLogDTO> result = activityLogService.listByProject(PROJECT_ID, 1, 20);

            assertThat(result.items().get(0).getDetails()).isNull();
        }
    }

    @Nested
    class ListForUser {

        @Test
        void shouldFilterByUserProjects() {
            ProjectMember member = new ProjectMember();
            member.setProjectId(PROJECT_ID);
            member.setUserId(USER_ID);

            ActivityLog log = log(1L, "commented", "task", 20L);
            User u = user(20L, "Charlie");

            when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member));
            when(activityLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(List.of(log)));
            when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(u));

            PageDTO<ActivityLogDTO> result = activityLogService.listForUser(USER_ID, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).getDisplayName()).isEqualTo("Charlie");
        }

        @Test
        void shouldReturnEmptyWhenUserHasNoProjects() {
            when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

            PageDTO<ActivityLogDTO> result = activityLogService.listForUser(USER_ID, 1, 20);

            assertThat(result.items()).isEmpty();
            verify(activityLogMapper, never()).selectPage(any(), any());
        }
    }

    private Page<ActivityLog> pageOf(List<ActivityLog> records) {
        Page<ActivityLog> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    private Page<ActivityLog> emptyPage() {
        Page<ActivityLog> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }
}

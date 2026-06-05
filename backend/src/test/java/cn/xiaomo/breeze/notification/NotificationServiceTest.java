package cn.xiaomo.breeze.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.notification.dto.NotificationDTO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationService notificationService;

    private static final Long USER_ID = 10L;
    private static final Long NOTIF_ID = 100L;

    private Notification notification(Long id, String type, String title, boolean isRead) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setType(type);
        n.setTitle(title);
        n.setBody("Test body");
        n.setIsRead(isRead);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    @Nested
    class ListByUser {

        @Test
        void shouldReturnNotificationsOrderedByUnreadFirst() {
            Notification n1 = notification(1L, "TASK_ASSIGNED", "Assigned", false);
            Notification n2 = notification(2L, "MENTIONED", "Mentioned", true);

            when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(List.of(n1, n2)));

            PageDTO<NotificationDTO> result = notificationService.listByUser(USER_ID, 1, 20);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().getFirst().getType()).isEqualTo("TASK_ASSIGNED");
            assertThat(result.items().getFirst().getIsRead()).isFalse();
            assertThat(result.items().get(1).getIsRead()).isTrue();
        }

        @Test
        void shouldReturnEmptyPage() {
            when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage());

            PageDTO<NotificationDTO> result = notificationService.listByUser(USER_ID, 1, 20);

            assertThat(result.items()).isEmpty();
        }
    }

    @Nested
    class UnreadCount {

        @Test
        void shouldCountUnreadNotifications() {
            when(notificationMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L);

            long count = notificationService.unreadCount(USER_ID);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        void shouldReturnZeroWhenNoUnread() {
            when(notificationMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

            long count = notificationService.unreadCount(USER_ID);

            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    class MarkRead {

        @Test
        void shouldMarkNotificationAsRead() {
            Notification n = notification(NOTIF_ID, "TASK_ASSIGNED", "Title", false);

            when(notificationMapper.selectById(NOTIF_ID)).thenReturn(n);

            notificationService.markRead(NOTIF_ID, USER_ID);

            assertThat(n.getIsRead()).isTrue();
            verify(notificationMapper).updateById(n);
        }

        @Test
        void shouldNotMarkOthersNotification() {
            Notification n = notification(NOTIF_ID, "TASK_ASSIGNED", "Title", false);
            n.setUserId(99L); // different user

            when(notificationMapper.selectById(NOTIF_ID)).thenReturn(n);

            notificationService.markRead(NOTIF_ID, USER_ID);

            assertThat(n.getIsRead()).isFalse();
            verify(notificationMapper, never()).updateById(any(Notification.class));
        }

        @Test
        void shouldHandleNonExistentNotification() {
            when(notificationMapper.selectById(99L)).thenReturn(null);

            notificationService.markRead(99L, USER_ID);

            verify(notificationMapper, never()).updateById(any(Notification.class));
        }
    }

    @Nested
    class MarkAllRead {

        @Test
        void shouldMarkAllUnreadAsRead() {
            notificationService.markAllRead(USER_ID);

            verify(notificationMapper).update(any(), any(UpdateWrapper.class));
        }
    }

    private Page<Notification> pageOf(List<Notification> records) {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    private Page<Notification> emptyPage() {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }
}

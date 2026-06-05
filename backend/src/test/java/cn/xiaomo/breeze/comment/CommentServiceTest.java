package cn.xiaomo.breeze.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.comment.dto.CommentDTO;
import cn.xiaomo.breeze.comment.dto.CreateCommentRequest;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.event.SseEmitterRegistry;
import cn.xiaomo.breeze.notification.Notification;
import cn.xiaomo.breeze.notification.NotificationMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private ActivityLogger activityLogger;

    @InjectMocks
    private CommentService commentService;

    private static final Long TASK_ID = 1L;
    private static final Long AUTHOR_ID = 10L;
    private static final Long ASSIGNEE_ID = 20L;
    private static final Long MENTIONED_ID = 30L;

    // -- fixtures --

    private Task task() {
        Task t = new Task();
        t.setId(TASK_ID);
        t.setKey("T-1");
        t.setTitle("Test Task");
        t.setAssigneeId(ASSIGNEE_ID);
        t.setIsDeleted(false);
        return t;
    }

    private User author() {
        User u = new User();
        u.setId(AUTHOR_ID);
        u.setUsername("alice");
        u.setDisplayName("Alice");
        return u;
    }

    private User assignee() {
        User u = new User();
        u.setId(ASSIGNEE_ID);
        u.setUsername("bob");
        u.setDisplayName("Bob");
        return u;
    }

    private User mentionedUser() {
        User u = new User();
        u.setId(MENTIONED_ID);
        u.setUsername("charlie");
        u.setDisplayName("Charlie");
        return u;
    }

    private Comment comment(Long id, Long taskId, Long parentId, Long userId, String content) {
        Comment c = new Comment();
        c.setId(id);
        c.setTaskId(taskId);
        c.setParentId(parentId);
        c.setUserId(userId);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private Comment topLevelComment() {
        return comment(1L, TASK_ID, null, AUTHOR_ID, "Great task!");
    }

    @Nested
    class Create {

        @Test
        void shouldCreateTopLevelComment() {
            Task t = task();
            User a = author();
            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Great task!");

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            CommentDTO result = commentService.create(TASK_ID, req, AUTHOR_ID);

            verify(commentMapper).insert(any(Comment.class));
            verify(notificationMapper).insert(any(Notification.class)); // COMMENT_ADDED to assignee
            verify(sseEmitterRegistry).send(eq(ASSIGNEE_ID), eq("notification"), anyString());
            assertThat(result.getTaskId()).isEqualTo(TASK_ID);
            assertThat(result.getUserId()).isEqualTo(AUTHOR_ID);
            assertThat(result.getContent()).isEqualTo("Great task!");
            assertThat(result.getUsername()).isEqualTo("alice");
        }

        @Test
        void shouldCreateReply() {
            Task t = task();
            User a = author();
            Comment parent = topLevelComment();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("I agree!");
            req.setParentId(1L);

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(commentMapper.selectById(1L)).thenReturn(parent);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            CommentDTO result = commentService.create(TASK_ID, req, AUTHOR_ID);

            assertThat(result.getParentId()).isEqualTo(1L);
        }

        @Test
        void shouldParseMentionsAndCreateNotifications() {
            Task t = task();
            User a = author();
            User m = mentionedUser();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Hey [@Charlie](/users/30) check this out");

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);
            lenient().when(userMapper.selectById(MENTIONED_ID)).thenReturn(m);

            commentService.create(TASK_ID, req, AUTHOR_ID);

            // COMMENT_ADDED for assignee + MENTIONED for Charlie
            verify(notificationMapper, times(2)).insert(any(Notification.class));
            verify(sseEmitterRegistry).send(eq(MENTIONED_ID), eq("notification"), contains("MENTIONED"));
        }

        @Test
        void shouldNotNotifySelfMention() {
            Task t = new Task();
            t.setId(TASK_ID);
            t.setKey("T-1");
            t.setAssigneeId(null); // no assignee, so no COMMENT_ADDED either
            t.setIsDeleted(false);

            User a = author();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("[@Alice](/users/10) self mention");

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            commentService.create(TASK_ID, req, AUTHOR_ID);

            // No notifications — self-mention skipped, no assignee
            verify(notificationMapper, never()).insert(any(Notification.class));
        }

        @Test
        void shouldNotNotifyAssigneeWhenAssigneeIsAuthor() {
            Task t = task();
            t.setAssigneeId(AUTHOR_ID); // author is also assignee
            User a = author();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("My own task");

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            commentService.create(TASK_ID, req, AUTHOR_ID);

            // No COMMENT_ADDED when commenter is assignee
            verify(notificationMapper, never()).insert(any(Notification.class));
        }

        @Test
        void shouldTruncateLongBodyInNotification() {
            Task t = task();
            User a = author();
            String longContent = "A".repeat(200);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent(longContent);

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            commentService.create(TASK_ID, req, AUTHOR_ID);

            verify(notificationMapper).insert(argThat((Notification n) ->
                n.getBody() != null && n.getBody().length() <= 103 && n.getBody().endsWith("...")
            ));
        }

        @Test
        void shouldRejectReplyToReply() {
            Task t = task();
            Comment parent = topLevelComment(); // parentId = null
            Comment nestedReply = new Comment();
            nestedReply.setId(2L);
            nestedReply.setParentId(1L); // this is already a reply

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Third level");
            req.setParentId(2L);

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(commentMapper.selectById(2L)).thenReturn(nestedReply);

            assertThatThrownBy(() -> commentService.create(TASK_ID, req, AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot reply to a reply");
        }

        @Test
        void shouldRejectReplyFromDifferentTask() {
            Task t = task();
            Comment parent = new Comment();
            parent.setId(1L);
            parent.setTaskId(999L); // different task
            parent.setParentId(null);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Wrong task");
            req.setParentId(1L);

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(commentMapper.selectById(1L)).thenReturn(parent);

            assertThatThrownBy(() -> commentService.create(TASK_ID, req, AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parent comment does not belong to this task");
        }

        @Test
        void shouldRejectWhenTaskNotFound() {
            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Test");

            when(taskMapper.selectById(TASK_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.create(TASK_ID, req, AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task not found");
        }

        @Test
        void shouldRejectWhenParentNotFound() {
            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("Test");
            req.setParentId(99L);

            when(taskMapper.selectById(TASK_ID)).thenReturn(task());
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> commentService.create(TASK_ID, req, AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parent comment not found");
        }
    }

    @Nested
    class ListByTask {

        @Test
        void shouldReturnCommentsWithReplies() {
            Comment c1 = comment(1L, TASK_ID, null, AUTHOR_ID, "Comment 1");
            Comment c2 = comment(2L, TASK_ID, null, ASSIGNEE_ID, "Comment 2");
            Comment r1 = comment(3L, TASK_ID, 1L, ASSIGNEE_ID, "Reply to c1");

            when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(List.of(c1, c2)));
            when(commentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(r1));
            when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(author(), assignee()));

            PageDTO<CommentDTO> result = commentService.listByTask(TASK_ID, 1, 20);

            assertThat(result.items()).hasSize(2);
            CommentDTO firstComment = result.items().getFirst();
            assertThat(firstComment.getId()).isEqualTo(1L);
            assertThat(firstComment.getReplies()).hasSize(1);
            assertThat(firstComment.getReplies().getFirst().getContent()).isEqualTo("Reply to c1");
        }

        @Test
        void shouldReturnEmptyWhenNoComments() {
            when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage());

            PageDTO<CommentDTO> result = commentService.listByTask(TASK_ID, 1, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isZero();
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateOwnComment() {
            Comment existing = topLevelComment();
            User a = author();

            when(commentMapper.selectById(1L)).thenReturn(existing);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            CommentDTO result = commentService.update(1L, "Updated content", AUTHOR_ID);

            assertThat(result.getContent()).isEqualTo("Updated content");
            verify(commentMapper).updateById(existing);
        }

        @Test
        void shouldRejectUpdateOtherUsersComment() {
            Comment existing = topLevelComment();
            when(commentMapper.selectById(1L)).thenReturn(existing);

            assertThatThrownBy(() -> commentService.update(1L, "Hacked", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot edit another user's comment");
        }

        @Test
        void shouldThrowWhenCommentNotFound() {
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> commentService.update(99L, "Update", AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Comment not found");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldDeleteOwnComment() {
            Comment existing = topLevelComment();
            when(commentMapper.selectById(1L)).thenReturn(existing);

            commentService.delete(1L, AUTHOR_ID);

            verify(commentMapper).deleteById(1L);
        }

        @Test
        void shouldRejectDeleteOtherUsersComment() {
            Comment existing = topLevelComment();
            when(commentMapper.selectById(1L)).thenReturn(existing);

            assertThatThrownBy(() -> commentService.delete(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot delete another user's comment");
        }

        @Test
        void shouldThrowWhenCommentNotFound() {
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> commentService.delete(99L, AUTHOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Comment not found");
        }
    }

    @Nested
    class MentionParsing {

        @Test
        void shouldExtractMultipleMentions() {
            Task t = task();
            User a = author();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("[@Alice](/users/10) and [@Bob](/users/20) please review");

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(userMapper.selectById(AUTHOR_ID)).thenReturn(a);

            commentService.create(TASK_ID, req, AUTHOR_ID);

            // MENTIONED for 10 (self, skipped) + MENTIONED for 20 + COMMENT_ADDED for assignee
            verify(notificationMapper, times(2)).insert(any(Notification.class));
            verify(sseEmitterRegistry).send(eq(20L), eq("notification"), contains("MENTIONED"));
        }
    }

    // -- helpers --

    private Page<Comment> pageOf(List<Comment> records) {
        Page<Comment> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    private Page<Comment> emptyPage() {
        Page<Comment> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        return page;
    }
}

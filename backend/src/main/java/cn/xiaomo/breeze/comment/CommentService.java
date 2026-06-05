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
import cn.xiaomo.breeze.notification.NotificationPayload;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskCollaborator;
import cn.xiaomo.breeze.task.TaskCollaboratorMapper;
import cn.xiaomo.breeze.task.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final TaskMapper taskMapper;
    private final TaskCollaboratorMapper taskCollaboratorMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ActivityLogger activityLogger;

    private static final Pattern MENTION_PATTERN = Pattern.compile("\\[@([^\\]]+)\\]\\(/users/(\\d+)\\)");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(rollbackFor = Exception.class)
    public CommentDTO create(Long taskId, CreateCommentRequest request, Long userId) {
        // Validate task exists
        Task task = taskMapper.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getIsDeleted())) {
            throw new IllegalArgumentException("Task not found");
        }

        // Validate one-level nesting
        if (request.getParentId() != null) {
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new IllegalArgumentException("Parent comment not found");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("Cannot reply to a reply");
            }
            if (!parent.getTaskId().equals(taskId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this task");
            }
        }

        Comment comment = new Comment();
        comment.setTaskId(taskId);
        comment.setParentId(request.getParentId());
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        // Parse @mentions and create notifications
        Set<Long> mentionedUserIds = parseMentions(request.getContent());
        User author = userMapper.selectById(userId);
        String authorName = author != null && author.getDisplayName() != null
            ? author.getDisplayName() : (author != null ? author.getUsername() : "Someone");

        for (Long mentionedId : mentionedUserIds) {
            if (!mentionedId.equals(userId)) {
                createNotification(mentionedId, "MENTIONED",
                    authorName + " 在任务 " + task.getKey() + " 中 @ 了你",
                    request.getContent().length() > 100
                        ? request.getContent().substring(0, 100) + "..."
                        : request.getContent(),
                    "comment", comment.getId());
            }
        }

        // Notify task assignee about new comment
        if (task.getAssigneeId() != null && !task.getAssigneeId().equals(userId)
                && !mentionedUserIds.contains(task.getAssigneeId())) {
            createNotification(task.getAssigneeId(), "COMMENT_ADDED",
                authorName + " 评论了任务 " + task.getKey(),
                request.getContent().length() > 100
                    ? request.getContent().substring(0, 100) + "..."
                    : request.getContent(),
                "task", taskId);
        }

        // Notify collaborators about new comment (skip author, assignee, and already-mentioned users)
        List<TaskCollaborator> collaborators = taskCollaboratorMapper.selectList(
            new LambdaQueryWrapper<TaskCollaborator>()
                .eq(TaskCollaborator::getTaskId, taskId));
        for (TaskCollaborator tc : collaborators) {
            if (tc.getUserId().equals(userId)
                    || tc.getUserId().equals(task.getAssigneeId())
                    || mentionedUserIds.contains(tc.getUserId())) {
                continue;
            }
            createNotification(tc.getUserId(), "COMMENT_ADDED",
                authorName + " 评论了任务 " + task.getKey(),
                request.getContent().length() > 100
                    ? request.getContent().substring(0, 100) + "..."
                    : request.getContent(),
                "task", taskId);
        }

        // 活动日志
        String preview = request.getContent().length() > 50
            ? request.getContent().substring(0, 50) + "..."
            : request.getContent();
        Map<String, Object> details = new HashMap<>();
        details.put("title", task.getTitle());
        details.put("preview", preview);
        activityLogger.log(task.getProjectId(), userId, "commented", "task", taskId, details);

        return toDTO(comment, author);
    }

    public PageDTO<CommentDTO> listByTask(Long taskId, int page, int size) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
            .eq(Comment::getTaskId, taskId)
            .isNull(Comment::getParentId)
            .orderByAsc(Comment::getCreatedAt);

        Page<Comment> result = commentMapper.selectPage(Page.of(page, size), wrapper);

        if (result.getRecords().isEmpty()) {
            return PageDTO.of(Collections.emptyList(), result.getTotal(), page, size);
        }

        List<Long> parentIds = result.getRecords().stream()
            .map(Comment::getId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<Comment>()
            .in(Comment::getParentId, parentIds)
            .orderByAsc(Comment::getCreatedAt);
        List<Comment> replies = commentMapper.selectList(replyWrapper);

        Set<Long> userIds = new HashSet<>();
        result.getRecords().forEach(c -> userIds.add(c.getUserId()));
        replies.forEach(r -> userIds.add(r.getUserId()));

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User u : users) {
                userMap.put(u.getId(), u);
            }
        }

        Map<Long, List<CommentDTO>> replyMap = new HashMap<>();
        for (Comment reply : replies) {
            User u = userMap.get(reply.getUserId());
            replyMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>())
                .add(toDTO(reply, u));
        }

        List<CommentDTO> dtos = result.getRecords().stream()
            .map(c -> {
                User u = userMap.get(c.getUserId());
                CommentDTO dto = toDTO(c, u);
                dto.setReplies(replyMap.getOrDefault(c.getId(), Collections.emptyList()));
                return dto;
            })
            .collect(Collectors.toList());

        return PageDTO.of(dtos, result.getTotal(), page, size);
    }

    public CommentDTO update(Long commentId, String content, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("Comment not found");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot edit another user's comment");
        }
        comment.setContent(content);
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        User user = userMapper.selectById(userId);
        return toDTO(comment, user);
    }

    public void delete(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("Comment not found");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot delete another user's comment");
        }
        commentMapper.deleteById(commentId);
    }

    private Set<Long> parseMentions(String content) {
        Set<Long> ids = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    @SneakyThrows
    private void createNotification(Long userId, String type, String title, String body,
                                     String referenceType, Long referenceId) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setType(type);
        notif.setTitle(title);
        notif.setBody(body);
        notif.setReferenceType(referenceType);
        notif.setReferenceId(referenceId);
        notif.setIsRead(false);
        notif.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notif);

        sseEmitterRegistry.send(userId, "notification",
            objectMapper.writeValueAsString(NotificationPayload.from(notif)));
    }

    private CommentDTO toDTO(Comment c, User user) {
        CommentDTO dto = new CommentDTO();
        dto.setId(c.getId());
        dto.setTaskId(c.getTaskId());
        dto.setParentId(c.getParentId());
        dto.setUserId(c.getUserId());
        dto.setContent(c.getContent());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setDisplayName(user.getDisplayName());
            dto.setAvatarUrl(user.getAvatarUrl());
        }
        return dto;
    }
}

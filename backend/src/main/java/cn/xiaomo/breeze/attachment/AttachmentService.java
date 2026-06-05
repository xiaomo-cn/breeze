package cn.xiaomo.breeze.attachment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final ActivityLogger activityLogger;
    private final String storageProvider;

    public AttachmentService(
        AttachmentMapper attachmentMapper,
        TaskMapper taskMapper,
        UserMapper userMapper,
        FileStorageService fileStorageService,
        ActivityLogger activityLogger,
        @Value("${app.storage.provider:local}") String storageProvider) {
        this.attachmentMapper = attachmentMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.fileStorageService = fileStorageService;
        this.activityLogger = activityLogger;
        this.storageProvider = storageProvider;
    }

    public AttachmentDTO upload(Long taskId, String fileName, String contentType, long fileSize,
                                InputStream inputStream, Long userId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getIsDeleted())) {
            throw new IllegalArgumentException("Task not found");
        }

        String storageKey = fileStorageService.store(fileName, contentType, fileSize, inputStream);

        Attachment att = new Attachment();
        att.setTaskId(taskId);
        att.setUserId(userId);
        att.setFileName(fileName);
        att.setFileSize(fileSize);
        att.setContentType(contentType);
        att.setStorageKey(storageKey);
        att.setStorageProvider(storageProvider);
        att.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(att);

        activityLogger.log(task.getProjectId(), userId, "uploaded", "task", taskId,
            Map.of("title", task.getTitle(), "fileName", fileName));

        return toDTO(att);
    }

    public List<AttachmentDTO> listByTask(Long taskId) {
        List<Attachment> attachments = attachmentMapper.selectList(
            new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getTaskId, taskId)
                .orderByDesc(Attachment::getCreatedAt));

        List<Long> userIds = attachments.stream()
            .map(Attachment::getUserId).distinct().toList();
        final Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
            : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return attachments.stream().map(a -> {
            AttachmentDTO dto = toDTO(a);
            User u = userMap.get(a.getUserId());
            if (u != null) dto.setUserName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
            return dto;
        }).toList();
    }

    public AttachmentDownload download(Long attachmentId) {
        Attachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) throw new IllegalArgumentException("Attachment not found");
        return new AttachmentDownload(att.getFileName(), att.getContentType(),
            fileStorageService.retrieve(att.getStorageKey()));
    }

    public void delete(Long attachmentId, Long userId) {
        Attachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) throw new IllegalArgumentException("Attachment not found");
        fileStorageService.delete(att.getStorageKey());
        attachmentMapper.deleteById(attachmentId);
    }

    private AttachmentDTO toDTO(Attachment a) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(a.getId());
        dto.setTaskId(a.getTaskId());
        dto.setUserId(a.getUserId());
        dto.setFileName(a.getFileName());
        dto.setFileSize(a.getFileSize());
        dto.setContentType(a.getContentType());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setStorageProvider(a.getStorageProvider());

        if (fileStorageService.supportsDirectUrl() && !"local".equals(a.getStorageProvider())) {
            dto.setUrl(fileStorageService.getUrl(a.getStorageKey(), a.getFileName()));
        } else {
            dto.setUrl("/api/v1/attachments/" + a.getId() + "/download");
        }
        return dto;
    }

    public record AttachmentDownload(String fileName, String contentType, InputStream stream) {}
}

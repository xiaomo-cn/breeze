package cn.xiaomo.breeze.attachment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.activity.ActivityLogger;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ActivityLogger activityLogger;

    @InjectMocks
    private AttachmentService attachmentService;

    private static final Long TASK_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long ATTACHMENT_ID = 100L;

    private Task task() {
        Task t = new Task();
        t.setId(TASK_ID);
        t.setProjectId(5L);
        t.setTitle("Test Task");
        t.setIsDeleted(false);
        return t;
    }

    @Nested
    class Upload {

        @Test
        void shouldUploadFile() {
            Task t = task();
            InputStream stream = new ByteArrayInputStream("test content".getBytes());

            when(taskMapper.selectById(TASK_ID)).thenReturn(t);
            when(fileStorageService.store(eq("test.txt"), eq("text/plain"), anyLong(), any()))
                .thenReturn("storage-key-123");

            AttachmentDTO result = attachmentService.upload(
                TASK_ID, "test.txt", "text/plain", 12L, stream, USER_ID);

            verify(attachmentMapper).insert(any(Attachment.class));
            verify(activityLogger).log(eq(5L), eq(USER_ID), eq("uploaded"), eq("task"),
                eq(TASK_ID), anyMap());
            assertThat(result.getFileName()).isEqualTo("test.txt");
            assertThat(result.getFileSize()).isEqualTo(12L);
            assertThat(result.getContentType()).isEqualTo("text/plain");
        }

        @Test
        void shouldRejectUploadToNonExistentTask() {
            when(taskMapper.selectById(TASK_ID)).thenReturn(null);

            InputStream stream = new ByteArrayInputStream("data".getBytes());
            assertThatThrownBy(() ->
                attachmentService.upload(TASK_ID, "file.txt", "text/plain", 4L, stream, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task not found");
        }

        @Test
        void shouldRejectUploadToDeletedTask() {
            Task t = task();
            t.setIsDeleted(true);
            when(taskMapper.selectById(TASK_ID)).thenReturn(t);

            InputStream stream = new ByteArrayInputStream("data".getBytes());
            assertThatThrownBy(() ->
                attachmentService.upload(TASK_ID, "file.txt", "text/plain", 4L, stream, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task not found");
        }
    }

    @Nested
    class ListByTask {

        @Test
        void shouldListAttachmentsWithUserNames() {
            Attachment att = new Attachment();
            att.setId(ATTACHMENT_ID);
            att.setTaskId(TASK_ID);
            att.setUserId(USER_ID);
            att.setFileName("doc.pdf");
            att.setFileSize(1024L);
            att.setContentType("application/pdf");

            User u = new User();
            u.setId(USER_ID);
            u.setDisplayName("Alice");

            when(attachmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(att));
            when(userMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(u));

            List<AttachmentDTO> result = attachmentService.listByTask(TASK_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getFileName()).isEqualTo("doc.pdf");
            assertThat(result.getFirst().getUserName()).isEqualTo("Alice");
        }
    }

    @Nested
    class Download {

        @Test
        void shouldReturnDownloadInfo() {
            Attachment att = new Attachment();
            att.setFileName("doc.pdf");
            att.setContentType("application/pdf");
            att.setStorageKey("storage-key");

            when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(att);
            when(fileStorageService.retrieve("storage-key"))
                .thenReturn(new ByteArrayInputStream("data".getBytes()));

            AttachmentService.AttachmentDownload download =
                attachmentService.download(ATTACHMENT_ID);

            assertThat(download.fileName()).isEqualTo("doc.pdf");
            assertThat(download.contentType()).isEqualTo("application/pdf");
        }

        @Test
        void shouldThrowWhenAttachmentNotFound() {
            when(attachmentMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> attachmentService.download(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attachment not found");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldDeleteFileAndRecord() {
            Attachment att = new Attachment();
            att.setStorageKey("storage-key");

            when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(att);

            attachmentService.delete(ATTACHMENT_ID, USER_ID);

            verify(fileStorageService).delete("storage-key");
            verify(attachmentMapper).deleteById(ATTACHMENT_ID);
        }

        @Test
        void shouldThrowWhenAttachmentNotFound() {
            when(attachmentMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> attachmentService.delete(99L, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attachment not found");
        }
    }
}

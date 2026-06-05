package cn.xiaomo.breeze.attachment;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttachmentDTO {
    private Long id;
    private Long taskId;
    private Long userId;
    private String userName;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String url;
    private String storageProvider;
    private LocalDateTime createdAt;
}

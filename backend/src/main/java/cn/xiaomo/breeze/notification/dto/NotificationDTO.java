package cn.xiaomo.breeze.notification.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotificationDTO {
    private Long id;
    private String type;
    private String title;
    private String body;
    private String referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

package cn.xiaomo.breeze.activity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ActivityLogDTO {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String actionType;
    private String entityType;
    private Long entityId;
    private String details;
    private LocalDateTime createdAt;
}

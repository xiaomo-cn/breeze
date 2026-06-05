package cn.xiaomo.breeze.activity;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityLogger {

    private final ActivityLogMapper activityLogMapper;

    public void log(Long projectId, Long userId, String actionType, String entityType, Long entityId, Map<String, Object> details) {
        ActivityLog log = new ActivityLog();
        log.setProjectId(projectId);
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        activityLogMapper.insert(log);
    }
}

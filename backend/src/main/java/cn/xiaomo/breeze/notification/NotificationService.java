package cn.xiaomo.breeze.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.notification.dto.NotificationDTO;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public PageDTO<NotificationDTO> listByUser(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .orderByAsc(Notification::getIsRead)
            .orderByDesc(Notification::getCreatedAt);

        Page<Notification> result = notificationMapper.selectPage(Page.of(page, size), wrapper);
        List<NotificationDTO> dtos = result.getRecords().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
        return PageDTO.of(dtos, result.getTotal(), page, size);
    }

    public long unreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .eq(Notification::getIsRead, false);
        return notificationMapper.selectCount(wrapper);
    }

    public void markRead(Long notificationId, Long userId) {
        Notification notif = notificationMapper.selectById(notificationId);
        if (notif != null && notif.getUserId().equals(userId)) {
            notif.setIsRead(true);
            notificationMapper.updateById(notif);
        }
    }

    public void markAllRead(Long userId) {
        UpdateWrapper<Notification> wrapper = new UpdateWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("is_read", false);
        wrapper.set("is_read", true);
        notificationMapper.update(null, wrapper);
    }

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setReferenceType(n.getReferenceType());
        dto.setReferenceId(n.getReferenceId());
        dto.setIsRead(n.getIsRead());
        dto.setCreatedAt(n.getCreatedAt());
        return dto;
    }
}

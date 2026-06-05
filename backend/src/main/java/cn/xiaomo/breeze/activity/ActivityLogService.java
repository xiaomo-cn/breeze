package cn.xiaomo.breeze.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public PageDTO<ActivityLogDTO> listByProject(Long projectId, int page, int size) {
        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<ActivityLog>()
            .eq(ActivityLog::getProjectId, projectId)
            .orderByDesc(ActivityLog::getCreatedAt);

        Page<ActivityLog> result = activityLogMapper.selectPage(Page.of(page, size), wrapper);
        return toDTOs(result, page, size);
    }

    public PageDTO<ActivityLogDTO> listForUser(Long userId, int page, int size) {
        List<Long> projectIds = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getUserId, userId))
            .stream()
            .map(ProjectMember::getProjectId)
            .toList();

        if (projectIds.isEmpty()) {
            return PageDTO.of(List.of(), 0L, page, size);
        }

        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<ActivityLog>()
            .in(ActivityLog::getProjectId, projectIds)
            .orderByDesc(ActivityLog::getCreatedAt);

        Page<ActivityLog> result = activityLogMapper.selectPage(Page.of(page, size), wrapper);
        return toDTOs(result, page, size);
    }

    private PageDTO<ActivityLogDTO> toDTOs(Page<ActivityLog> result, int page, int size) {
        List<Long> userIds = result.getRecords().stream()
            .map(ActivityLog::getUserId)
            .distinct()
            .toList();

        final Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
            : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ActivityLogDTO> dtos = result.getRecords().stream()
            .map(log -> {
                ActivityLogDTO dto = new ActivityLogDTO();
                dto.setId(log.getId());
                dto.setProjectId(log.getProjectId());
                dto.setUserId(log.getUserId());
                dto.setActionType(log.getActionType());
                dto.setEntityType(log.getEntityType());
                dto.setEntityId(log.getEntityId());
                dto.setDetails(toJson(log.getDetails()));
                dto.setCreatedAt(log.getCreatedAt());
                User u = userMap.get(log.getUserId());
                if (u != null) {
                    dto.setUsername(u.getUsername());
                    dto.setDisplayName(u.getDisplayName());
                    dto.setAvatarUrl(u.getAvatarUrl());
                }
                return dto;
            })
            .toList();

        return PageDTO.of(dtos, result.getTotal(), page, size);
    }

    private static String toJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}

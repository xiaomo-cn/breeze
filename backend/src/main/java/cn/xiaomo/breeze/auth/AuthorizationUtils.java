package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import cn.xiaomo.breeze.project.ProjectRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 项目级权限检查工具。
 * 查询 project_members 表，判断用户是否拥有项目内操作所需的最低角色。
 */
@Component
@RequiredArgsConstructor
public class AuthorizationUtils {

    private final ProjectMemberMapper projectMemberMapper;

    /**
     * 要求用户必须是项目成员且角色不低于 minimumRole，否则抛出 AccessDeniedException。
     */
    public void requireProjectRole(Long projectId, Long userId, ProjectRole minimumRole) {
        ProjectMember member = projectMemberMapper.selectOne(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (member == null) {
            throw new AccessDeniedException("你不是该项目的成员");
        }
        ProjectRole userRole = parseRole(member.getRole());
        if (userRole == null || !userRole.isAtLeast(minimumRole)) {
            throw new AccessDeniedException(
                "此操作需要 " + minimumRole.value() + " 或更高权限");
        }
    }

    /**
     * 获取用户在项目中的角色，非成员返回 null。
     */
    public ProjectRole getProjectRole(Long projectId, Long userId) {
        ProjectMember member = projectMemberMapper.selectOne(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (member == null) return null;
        return parseRole(member.getRole());
    }

    private ProjectRole parseRole(String role) {
        if (role == null) return null;
        try {
            return ProjectRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

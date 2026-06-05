package cn.xiaomo.breeze.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.board.BoardService;
import cn.xiaomo.breeze.common.PageDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final BoardService boardService;

    @Transactional(rollbackFor = Exception.class)
    public Project create(Project project, Long ownerId) {
        if (projectMapper.exists(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, project.getKey()))) {
            throw new IllegalArgumentException("Project key already exists");
        }
        project.setOwnerId(ownerId);
        project.setStatus("active");
        projectMapper.insert(project);

        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(ownerId);
        member.setRole("admin");
        memberMapper.insert(member);

        boardService.initDefaultBoard(project.getId());

        return project;
    }

    public PageDTO<Project> listByUser(Long userId, int page, int size, String status) {
        List<Long> projectIds = memberMapper.selectList(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getUserId, userId)
                .select(ProjectMember::getProjectId))
            .stream().map(ProjectMember::getProjectId).toList();

        if (projectIds.isEmpty()) {
            return PageDTO.of(List.of(), 0, page, size);
        }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
            .in(Project::getId, projectIds);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, status);
        }
        wrapper.orderByDesc(Project::getCreatedAt);

        IPage<Project> result = projectMapper.selectPage(Page.of(page, size), wrapper);
        return PageDTO.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Cacheable(value = "project", key = "#id")
    public Project getById(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }
        return project;
    }

    @CacheEvict(value = "project", key = "#id")
    public Project update(Long id, Project updates) {
        Project project = getById(id);
        if (updates.getName() != null) project.setName(updates.getName());
        if (updates.getDescription() != null) project.setDescription(updates.getDescription());
        if (updates.getStatus() != null) project.setStatus(updates.getStatus());
        projectMapper.updateById(project);
        return project;
    }

    // --- Member management ---

    @Cacheable(value = "project_members", key = "#projectId")
    public List<ProjectMember> listMembers(Long projectId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
            .eq(ProjectMember::getProjectId, projectId)
            .orderByAsc(ProjectMember::getJoinedAt));
    }

    @CacheEvict(value = "project_members", key = "#projectId")
    @Transactional(rollbackFor = Exception.class)
    public ProjectMember addMember(Long projectId, Long userId, String role) {
        if (memberMapper.exists(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId))) {
            throw new IllegalArgumentException("User is already a member of this project");
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        String resolvedRole = (role != null && ProjectRole.isValid(role)) ? role : ProjectRole.MEMBER.value();
        member.setRole(resolvedRole);
        memberMapper.insert(member);
        return member;
    }

    @CacheEvict(value = "project_members", key = "#projectId")
    public void removeMember(Long projectId, Long userId) {
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
            .eq(ProjectMember::getProjectId, projectId)
            .eq(ProjectMember::getUserId, userId));
    }

    @CacheEvict(value = "project_members", key = "#projectId")
    public void updateMemberRole(Long projectId, Long userId, String role) {
        ProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
            .eq(ProjectMember::getProjectId, projectId)
            .eq(ProjectMember::getUserId, userId));
        if (member == null) {
            throw new IllegalArgumentException("Member not found");
        }
        if (!ProjectRole.isValid(role)) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        member.setRole(role);
        memberMapper.updateById(member);
    }
}

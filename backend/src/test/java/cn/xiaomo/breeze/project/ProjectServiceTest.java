package cn.xiaomo.breeze.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.common.PageDTO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectMemberMapper memberMapper;
    @Mock
    private cn.xiaomo.breeze.board.BoardService boardService;

    @InjectMocks
    private ProjectService projectService;

    private static final Long OWNER_ID = 1L;
    private static final Long PROJECT_ID = 10L;

    /**
     * 预初始化 MyBatis-Plus TableInfo 缓存。
     * 纯 Mockito 测试没有 Spring 上下文，LambdaQueryWrapper 的 lambda 解析
     * 需要实体类在 TableInfoHelper 中有注册。
     */
    @BeforeAll
    static void initMybatisPlusEntities() {
        Configuration config = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "test");
        TableInfoHelper.initTableInfo(assistant, Project.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMember.class);
    }

    @Nested
    class Create {

        @Test
        void shouldCreateProjectAndAddOwnerAsAdmin() {
            when(projectMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
            when(projectMapper.insert(any(Project.class))).thenAnswer(inv -> {
                Project p = inv.getArgument(0);
                p.setId(PROJECT_ID);
                return 1;
            });
            when(memberMapper.insert(any(ProjectMember.class))).thenReturn(1);

            Project project = new Project();
            project.setName("My Project");
            project.setKey("MYPROJ");

            Project result = projectService.create(project, OWNER_ID);

            assertThat(result.getId()).isEqualTo(PROJECT_ID);
            assertThat(result.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(result.getStatus()).isEqualTo("active");

            verify(memberMapper).insert(ArgumentMatchers.<ProjectMember>argThat(m ->
                    m.getProjectId().equals(PROJECT_ID) &&
                    m.getUserId().equals(OWNER_ID) &&
                    "admin".equals(m.getRole())));
        }

        @Test
        void shouldThrowWhenKeyExists() {
            when(projectMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            Project project = new Project();
            project.setKey("DUP");

            assertThatThrownBy(() -> projectService.create(project, OWNER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Project key already exists");

            verify(projectMapper, never()).insert(ArgumentMatchers.<Project>any());
        }
    }

    @Nested
    class ListByUser {

        @Test
        void shouldReturnUserProjects() {
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member(PROJECT_ID, OWNER_ID, "admin")));
            when(projectMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of(project())));

            PageDTO<Project> result = projectService.listByUser(OWNER_ID, 1, 10, null);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).hasSize(1);
        }

        @Test
        void shouldReturnEmptyWhenNoMemberships() {
            when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            PageDTO<Project> result = projectService.listByUser(OWNER_ID, 1, 10, null);

            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        void shouldFilterByStatus() {
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member(PROJECT_ID, OWNER_ID, "admin")));
            when(projectMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageOf(List.of(project())));

            projectService.listByUser(OWNER_ID, 1, 10, "active");

            verify(projectMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    class GetById {

        @Test
        void shouldReturnProject() {
            Project p = project();
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(p);

            Project result = projectService.getById(PROJECT_ID);

            assertThat(result).isEqualTo(p);
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(projectMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> projectService.getById(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Project not found");
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateFields() {
            Project existing = project();
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(existing);
            when(projectMapper.updateById(any(Project.class))).thenReturn(1);

            Project updates = new Project();
            updates.setName("New Name");
            updates.setStatus("archived");

            Project result = projectService.update(PROJECT_ID, updates);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getStatus()).isEqualTo("archived");
            verify(projectMapper).updateById(existing);
        }

        @Test
        void shouldThrowWhenProjectNotFound() {
            when(projectMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> projectService.update(999L, new Project()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Members {

        @Test
        void shouldListMembers() {
            List<ProjectMember> members = List.of(member(PROJECT_ID, OWNER_ID, "admin"));
            when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(members);

            List<ProjectMember> result = projectService.listMembers(PROJECT_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void shouldAddMember() {
            when(memberMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
            when(memberMapper.insert(any(ProjectMember.class))).thenAnswer(inv -> {
                ProjectMember m = inv.getArgument(0);
                m.setId(100L);
                return 1;
            });

            ProjectMember result = projectService.addMember(PROJECT_ID, 2L, "manager");

            assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(result.getUserId()).isEqualTo(2L);
            assertThat(result.getRole()).isEqualTo("manager");
        }

        @Test
        void shouldAddMemberWithDefaultRole() {
            when(memberMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
            when(memberMapper.insert(any(ProjectMember.class))).thenReturn(1);

            ProjectMember result = projectService.addMember(PROJECT_ID, 3L, null);

            assertThat(result.getRole()).isEqualTo("member");
        }

        @Test
        void shouldThrowWhenAlreadyMember() {
            when(memberMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, OWNER_ID, "editor"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User is already a member of this project");
        }

        @Test
        void shouldRemoveMember() {
            when(memberMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            projectService.removeMember(PROJECT_ID, 2L);

            verify(memberMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        void shouldUpdateMemberRole() {
            ProjectMember m = member(PROJECT_ID, 2L, "member");
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(m);
            when(memberMapper.updateById(any(ProjectMember.class))).thenReturn(1);

            projectService.updateMemberRole(PROJECT_ID, 2L, "admin");

            assertThat(m.getRole()).isEqualTo("admin");
            verify(memberMapper).updateById(m);
        }

        @Test
        void shouldThrowWhenMemberNotFound() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> projectService.updateMemberRole(PROJECT_ID, 999L, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Member not found");
        }
    }

    // -- helpers --

    private Project project() {
        Project p = new Project();
        p.setId(PROJECT_ID);
        p.setName("Test Project");
        p.setKey("TEST");
        p.setOwnerId(OWNER_ID);
        p.setStatus("active");
        return p;
    }

    private ProjectMember member(Long projectId, Long userId, String role) {
        ProjectMember m = new ProjectMember();
        m.setId(1L);
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    private Page<Project> pageOf(List<Project> records) {
        Page<Project> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }
}

package cn.xiaomo.breeze.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目成员实体，映射 project_members 表（多对多关联）。
 */
@Data
@TableName("project_members")
public class ProjectMember {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 用户 ID */
    private Long userId;

    /** 角色：owner / admin / member */
    private String role;

    /** 加入时间 */
    private LocalDateTime joinedAt;
}

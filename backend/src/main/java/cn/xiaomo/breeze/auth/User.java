package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户实体，映射 users 表。
 */
@Data
@TableName("users")
public class User {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名，唯一 */
    private String username;

    /** 邮箱，唯一 */
    private String email;

    /** bcrypt 加密后的密码哈希 */
    private String passwordHash;

    /** 显示名称（昵称） */
    private String displayName;

    /** 头像 URL */
    private String avatarUrl;

    /** 职位/头衔（自由文本） */
    private String title;

    /** 职务ID，关联 positions 表 */
    private Long positionId;

    /** 部门 */
    private String department;

    /** 时区，如 Asia/Shanghai */
    private String timezone;

    /** 语言偏好，如 zh-CN */
    private String locale;

    /** 系统级角色：system_admin 或 user */
    private String role;

    /** 是否需要在下次登录时强制修改密码 */
    private Boolean mustChangePassword;

    /** 是否启用 */
    private Boolean isActive;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}

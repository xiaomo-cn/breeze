package cn.xiaomo.breeze.project;

/**
 * 项目级角色枚举，用于 project_members.role 字段。
 * 权限高低：ADMIN > MANAGER > MEMBER > VIEWER
 * ordinal 越小权限越高，{@link #isAtLeast(ProjectRole)} 利用此特性比较。
 */
public enum ProjectRole {
    ADMIN("admin"),
    MANAGER("manager"),
    MEMBER("member"),
    VIEWER("viewer");

    private final String value;

    ProjectRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 判断当前角色是否不低于指定角色。
     * ADMIN (ordinal=0) 最高，VIEWER (ordinal=3) 最低。
     */
    public boolean isAtLeast(ProjectRole minimum) {
        return this.ordinal() <= minimum.ordinal();
    }

    public static boolean isValid(String role) {
        for (ProjectRole r : values()) {
            if (r.value.equals(role)) return true;
        }
        return false;
    }
}

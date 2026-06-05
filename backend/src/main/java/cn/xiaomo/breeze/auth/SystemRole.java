package cn.xiaomo.breeze.auth;

/**
 * 系统级角色枚举。
 * 只有 system_admin 可以创建/管理用户账号。
 */
public enum SystemRole {
    SYSTEM_ADMIN("system_admin"),
    USER("user");

    private final String value;

    SystemRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean isValid(String role) {
        for (SystemRole r : values()) {
            if (r.value.equals(role)) return true;
        }
        return false;
    }
}

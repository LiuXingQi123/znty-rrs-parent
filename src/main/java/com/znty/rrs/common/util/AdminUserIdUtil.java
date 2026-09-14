package com.znty.rrs.common.util;

/**
 * 管理员用户 ID 判断工具。
 */
public final class AdminUserIdUtil {

    /** 默认管理员用户 ID。 */
    private static final String DEFAULT_ADMIN_USER_ID = "1";
    /** 预留管理员用户 ID 起始值。 */
    private static final long RESERVED_ADMIN_USER_ID_START = 10000L;
    /** 预留管理员用户 ID 结束值。 */
    private static final long RESERVED_ADMIN_USER_ID_END = 10100L;

    private AdminUserIdUtil() {
    }

    /**
     * 判断用户 ID 是否具备管理员权限。
     *
     * @param userId 用户 ID
     * @return 默认管理员或预留管理员 ID 段内的用户返回 true
     */
    public static boolean isAdminUser(String userId) {
        if (userId == null) {
            return false;
        }
        String normalizedUserId = userId.trim();
        if (DEFAULT_ADMIN_USER_ID.equals(normalizedUserId)) {
            return true;
        }
        try {
            long numericUserId = Long.parseLong(normalizedUserId);
            return numericUserId >= RESERVED_ADMIN_USER_ID_START
                    && numericUserId <= RESERVED_ADMIN_USER_ID_END;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

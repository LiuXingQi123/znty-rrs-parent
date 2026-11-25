package com.znty.rrs.common.util;

/**
 * 管理员用户 ID 判断工具
 *
 * 按系统约定识别默认管理员和预留区间内的管理员
 */
public final class AdminUserIdUtil {

    /** 默认管理员用户 ID */
    private static final String DEFAULT_ADMIN_USER_ID = "1";

    /** 预留管理员用户 ID 下限，含本值 */
    private static final long RESERVED_ADMIN_USER_ID_START = 10000L;

    /** 预留管理员用户 ID 上限，含本值 */
    private static final long RESERVED_ADMIN_USER_ID_END = 10100L;

    /** 工具类禁止实例化 */
    private AdminUserIdUtil() {
    }

    /**
     * 判断用户 ID 是否属于系统约定的管理员范围
     *
     * 执行顺序：
     * 1. ID 缺失时返回 false，去掉首尾空白后先匹配默认管理员
     * 2. 其余 ID 转成整数，无法转换时返回 false，否则判断是否在预留区间内
     *
     * @param userId 用户 ID
     * @return true=默认管理员或预留管理员，false=用户 ID 无效或不属于管理员范围
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

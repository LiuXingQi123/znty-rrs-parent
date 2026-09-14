package com.znty.rrs.common.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理员用户 ID 判断工具测试。 */
public class AdminUserIdUtilTest {

    /** 验证默认管理员和预留管理员 ID 段。 */
    @Test
    public void shouldIdentifyDefaultAndReservedAdminUserIds() {
        assertThat(AdminUserIdUtil.isAdminUser("1")).isTrue();
        assertThat(AdminUserIdUtil.isAdminUser("10000")).isTrue();
        assertThat(AdminUserIdUtil.isAdminUser("10100")).isTrue();
    }

    /** 验证预留管理员 ID 段以外的用户。 */
    @Test
    public void shouldRejectNonAdminUserIds() {
        assertThat(AdminUserIdUtil.isAdminUser(null)).isFalse();
        assertThat(AdminUserIdUtil.isAdminUser("")).isFalse();
        assertThat(AdminUserIdUtil.isAdminUser("admin")).isFalse();
        assertThat(AdminUserIdUtil.isAdminUser("9999")).isFalse();
        assertThat(AdminUserIdUtil.isAdminUser("10101")).isFalse();
    }
}

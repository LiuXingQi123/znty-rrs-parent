package com.znty.rrs.common.constants;

/** 业务功能开关常量，修改后需重新编译并重启服务 */
public final class BusinessSwitches {

    /** 是否启用强担保对重点观察名单准入及池间调入限制的豁免 */
    public static final boolean STRONG_GUARANTEE_ENABLED = false;

    /** 禁止实例化 */
    private BusinessSwitches() {
    }
}

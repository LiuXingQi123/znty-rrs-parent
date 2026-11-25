package com.znty.rrs.common.constants;

/**
 * 调库业务开关
 *
 * 开关值写在代码中，修改后需重新编译并重启服务
 */
public final class BusinessSwitches {

    /**
     * 控制强担保是否可以豁免重点观察名单准入限制和指定的池间调入限制
     *
     * true 时，满足强担保豁免条件的证券可按规则豁免相应限制
     * false 时，强担保证券仍按常规规则检查这两项限制
     */
    public static final boolean STRONG_GUARANTEE_ENABLED = false;

    /**
     * 控制产品库快速入库和材料豁免可使用哪些 CRMW 在池记录
     *
     * true 时，普通池表 {@code ip_pool_status} 或 CRMW 组合表 {@code ip_pool_status_crmw} 任一有有效在池记录即可
     * false 时，只认可 CRMW 组合表中的有效在池记录
     * 查询 CRMW 组合时，按标的证券代码 {@code security_code} 匹配
     */
    public static final boolean CRMW_USE_POOL_STATUS = true;

    /**
     * 控制信用债简易入库是否要求同发行人有近期有效信评报告
     *
     * true 时，同发行人近 180 天实际入库的调入业务须有有效信评报告附件，才能匹配简易入库流程
     * false 时，按简易入库的其他条件匹配流程
     */
    public static final boolean SIMPLE_INBOUND_RECENT_REPORT_REQUIRED = false;

    /** 禁止实例化常量类 */
    private BusinessSwitches() {
    }
}

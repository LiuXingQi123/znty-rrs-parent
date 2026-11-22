package com.znty.rrs.common.constants;

/** 业务功能开关常量，修改后需重新编译并重启服务 */
public final class BusinessSwitches {

    /** 是否启用强担保对重点观察名单准入及池间调入限制的豁免 */
    public static final boolean STRONG_GUARANTEE_ENABLED = false;

    /**
     * 产品库快速入库和材料豁免的 CRMW 来源开关
     * true 时，ip_pool_status 或 ip_pool_status_crmw 中任一 CRMW 库有效在池记录即可作为依据
     * false 时，只认可 ip_pool_status_crmw 中的 CRMW 库有效在池记录
     * ip_pool_status_crmw 按标的证券代码 security_code 匹配
     */
    public static final boolean CRMW_USE_POOL_STATUS = true;

    /**
     * 信用债简易入库是否强制要求同发行人近180天实际入库业务中有有效信评报告
     * true 时，同发行人近180天已入库的调入业务必须包含有效信评报告附件才能匹配简易入库流程
     * false 时，暂不要求此条件（默认值）
     */
    public static final boolean SIMPLE_INBOUND_RECENT_REPORT_REQUIRED = false;

    /** 禁止实例化 */
    private BusinessSwitches() {
    }
}

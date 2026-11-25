package com.znty.rrs.common.enums;

/**
 * 投资池类型
 *
 * 对应 {@code ip_investment_pool.pool_type}，按业务用途给池分类，池名称可独立设置，同一类型可配置多棵树或多个产品库
 * 新增业务类型时同步补充后端枚举和前端字典，暂时没有专门规则的类型用于归类和选池过滤
 * 后端保存池类型时只要求编码非空，便于类型过渡
 * {@code credit_bond}、{@code crmw}、{@code forbidden}、{@code blacklist}、{@code observe} 和 {@code restricted} 有专门的查询或校验规则，调整编码时须同步核对 Mapper 和 Service
 */
public enum PoolType {

    // ── 固收 / 债券 ──

    /**
     * 信用债大库，升降级流程、主体内评矩阵和互斥特殊审批排除等规则使用此编码
     */
    CREDIT_BOND("credit_bond"),

    /**
     * 境外债库按通用调库规则和市场限制校验
     *
     * 信用债主体评分档规则只用于信用债大库，适用范围沿用老系统 {@code polidEnum}
     */
    OFFSHORE_BOND("offshore_bond"),

    /**
     * 转债库 / 转债产品库
     */
    CONVERTIBLE_BOND("convertible_bond"),

    /**
     * 债券产品库（产品维度名单，区别于信用债大库）
     */
    BOND_PRODUCT("bond_product"),

    /**
     * 专户债券 / 专户产品库
     */
    SPECIAL_ACCOUNT("special_account"),

    // ── 权益 / 股票 ──

    /**
     * 公司股票库、公司港股库等研究/主体维度股票池
     */
    STOCK("stock"),

    /**
     * 股票产品库（A股/H股等产品维度）
     */
    STOCK_PRODUCT("stock_product"),

    // ── 基金 ──

    /**
     * 公司基金库、基金产品库
     */
    FUND("fund"),

    // ── 风险控制（禁投 / 名单） ──

    /**
     * 禁止库，含量化和指数产品禁止等类型
     *
     * 在禁止库中时禁止调入其他池，禁止库也纳入禁投查询范围
     */
    FORBIDDEN("forbidden"),

    /**
     * 观察池纳入禁投相关目标，调入分级库时不得高于标准矩阵允许的最好档
     */
    OBSERVE("observe"),

    /**
     * 黑名单供风险池查询使用，全局调入禁止规则只适用于 {@link #FORBIDDEN}
     */
    BLACKLIST("blacklist"),

    /**
     * 限制名单（重点观察）
     *
     * 禁止新增调入信用债一至五级库，符合强担保条件时可按开关豁免
     * 已在分级库时只允许调入五级或办理出库
     */
    RESTRICTED("restricted"),

    /**
     * 白名单库（流程/规则白名单池，预留）
     */
    WHITELIST("whitelist"),

    // ── 独立链路 / 兜底 ──

    /**
     * CRMW 库，使用独立的 CRMW 调库链路
     */
    CRMW("crmw"),

    /**
     * 其他 / 自定义根池（如临时命名根节点）
     */
    OTHER("other");

    /**
     * 投资池类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的投资池类型编码
     */
    PoolType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的投资池类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}

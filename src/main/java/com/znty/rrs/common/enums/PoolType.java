package com.znty.rrs.common.enums;

/**
 * 投资池类型（对应 ip_investment_pool.pool_type）。
 * <p>
 * 定位：业务域标签 + 少量硬规则开关（与池「名称」无关；同一类型下可挂多棵树/多个产品库）。
 * 扩展约定：新增业务域时在本枚举与前端字典同步增加 code；硬逻辑仅绑定下方标注的类型，
 * 其余类型先作分类与选池过滤，后续按需挂规则。后端入库不强制枚举白名单（非空即可），便于过渡。
 * <p>
 * 硬逻辑（改 code 需同步改 Mapper/Service）：
 * credit_bond / crmw / forbidden / blacklist / observe / restricted
 */
public enum PoolType {

    // ── 固收 / 债券 ──

    /** 信用债大库：升降级流程、主体内评矩阵、互斥特殊审批排除等（硬逻辑） */
    CREDIT_BOND("credit_bond"),
    /** 境外债库 */
    OFFSHORE_BOND("offshore_bond"),
    /** 转债库 / 转债产品库 */
    CONVERTIBLE_BOND("convertible_bond"),
    /** 债券产品库（产品维度名单，区别于信用债大库） */
    BOND_PRODUCT("bond_product"),
    /** 专户债券 / 专户产品库 */
    SPECIAL_ACCOUNT("special_account"),

    // ── 权益 / 股票 ──

    /** 公司股票库、公司港股库等研究/主体维度股票池 */
    STOCK("stock"),
    /** 股票产品库（A股/H股等产品维度） */
    STOCK_PRODUCT("stock_product"),

    // ── 基金 ──

    /** 公司基金库、基金产品库 */
    FUND("fund"),

    // ── 风险控制（禁投 / 名单） ──

    /** 禁止库（含量化/指数产品禁止等）：全局禁止入其它池；禁投查询范围（硬逻辑） */
    FORBIDDEN("forbidden"),
    /** 观察池：主体内评矩阵可跳过；禁投相关目标（硬逻辑） */
    OBSERVE("observe"),
    /** 黑名单：风险池查询用，不参与全局禁止（全局禁止仅 forbidden） */
    BLACKLIST("blacklist"),
    /** 限制名单（重点观察等），参与风险池查询，无全局禁止硬逻辑 */
    RESTRICTED("restricted"),
    /** 白名单库（流程/规则白名单池，预留） */
    WHITELIST("whitelist"),

    // ── 独立链路 / 兜底 ──

    /** CRMW 库：CRMW 独立调库链路（硬逻辑） */
    CRMW("crmw"),
    /** 其他 / 自定义根池（如临时命名根节点） */
    OTHER("other");

    /** 枚举 code 值 */
    private final String code;

    PoolType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}

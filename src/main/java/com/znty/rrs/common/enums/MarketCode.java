package com.znty.rrs.common.enums;

import java.util.ArrayList;
import java.util.List;

/**
 * 证券和投资市场编码
 *
 * 投资池 {@code market_codes}、临时代码市场、批量筛选及调库校验统一使用本枚举中的编码
 * 支持 {@code SSE}、{@code SZSE}、{@code NIB}、{@code BSE}、{@code COMPANY}、{@code OTC}、
 * {@code HKEX}、{@code QDII}、{@code JWCW}、{@code UNKNOWN} 和 {@code OTHER}
 * 中文名称由前端字典维护，后端使用稳定的市场编码
 */
public enum MarketCode {

    /**
     * 上海证券交易所
     */
    SSE("SSE"),

    /**
     * 深圳证券交易所
     */
    SZSE("SZSE"),

    /**
     * 银行间市场
     */
    NIB("NIB"),

    /**
     * 北京证券交易所
     */
    BSE("BSE"),

    /**
     * 主体
     */
    COMPANY("COMPANY"),

    /**
     * 场外市场
     */
    OTC("OTC"),

    /**
     * 香港交易所
     */
    HKEX("HKEX"),

    /**
     * 其他 QDII 市场
     */
    QDII("QDII"),

    /**
     * JWCW 市场（境外债等）
     */
    JWCW("JWCW"),

    /**
     * 未知
     */
    UNKNOWN("UNKNOWN"),

    /**
     * 其他
     */
    OTHER("OTHER");

    /**
     * 证券市场编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的证券市场编码
     */
    MarketCode(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的证券市场编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 取得投资池选项和 Wind 同券关联筛选共用的市场编码
     *
     * @return 按枚举声明顺序生成的市场编码列表
     */
    public static List<String> queryCodeList() {
        List<String> codes = new ArrayList<>();
        for (MarketCode market : values()) {
            codes.add(market.getCode());
        }
        return codes;
    }

    /**
     * 校验证券市场编码
     *
     * 执行顺序：
     * 1. 输入为 null 或空字符串时返回 false
     * 2. 使用原值逐项精确比较，大小写和空白均参与匹配
     *
     * @param code 待校验的证券市场编码
     * @return true=原值与某个市场编码完全一致，false=输入为空或未命中
     */
    public static boolean isValid(String code) {
        // 1. 缺少编码时直接返回校验失败
        if (code == null || code.isEmpty()) {
            return false;
        }
        // 2. 保留输入原值，逐项精确比较市场编码
        for (MarketCode item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}

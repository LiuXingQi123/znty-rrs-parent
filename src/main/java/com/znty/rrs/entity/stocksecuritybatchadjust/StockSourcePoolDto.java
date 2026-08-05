package com.znty.rrs.entity.stocksecuritybatchadjust;

import lombok.Data;

/**
 * 存量证券批量调整「来源池」下拉项。
 * 中文展示名由前端根据 poolCode 映射，后端只返 code / id / 名称字段。
 */
@Data
public class StockSourcePoolDto {

    /** 投资池 ID */
    private Long id;

    /** 投资池编码（稳定键） */
    private String poolCode;

    /** 投资池名称 */
    private String poolName;

    /** 投资池全路径名称（可选展示） */
    private String poolFullName;
}

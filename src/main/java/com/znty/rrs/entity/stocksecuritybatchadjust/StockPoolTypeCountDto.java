package com.znty.rrs.entity.stocksecuritybatchadjust;

import lombok.Data;

/**
 * 投资池在池数量按证券类型分项（批量页「现有数量」展示）
 *
 * typeCode 为业务分类 code，中文名由前端字典展示
 */
@Data
public class StockPoolTypeCountDto {

    /** 投资池 ID（仅统计查询回填用，列表嵌套项可不带） */
    private Long poolId;

    /**
     * 类型编码，其他大类对齐 dict_security_type.category_type
     *
     * company=主体、crmw=CRMW 凭证、bond=债券、stock=股票、fund=基金
     */
    private String typeCode;

    /** 该类型在池有效数量 */
    private Integer count;
}

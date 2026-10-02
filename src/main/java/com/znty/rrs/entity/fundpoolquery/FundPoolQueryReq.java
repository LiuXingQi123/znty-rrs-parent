package com.znty.rrs.entity.fundpoolquery;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池查询请求对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FundPoolQueryReq extends PageRequest {
    /** 投资池树选中节点 ID 列表。 */
    private List<Long> poolIds;
    /** 基金代码（模糊搜索）。 */
    private String fundCode;
    /** 基金名称（模糊搜索，包含简称）。 */
    private String fundName;
    /** 基金产品类型（精确匹配）。 */
    private String securityType;
    /** 入池时间起。 */
    private String entryTimeStart;
    /** 入池时间止。 */
    private String entryTimeEnd;
    /** 调整人（模糊搜索）。 */
    private String adjusterName;
}

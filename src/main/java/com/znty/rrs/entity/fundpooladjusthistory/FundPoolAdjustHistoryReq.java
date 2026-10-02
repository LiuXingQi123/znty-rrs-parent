package com.znty.rrs.entity.fundpooladjusthistory;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池调整历史查询请求对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FundPoolAdjustHistoryReq extends PageRequest {
    /** 投资池树选中节点 ID 列表。 */
    private List<Long> poolIds;
    /** 基金代码（模糊搜索）。 */
    private String fundCode;
    /** 基金名称（模糊搜索，包含简称）。 */
    private String fundName;
    /** 基金产品类型（精确匹配）。 */
    private String securityType;
    /** 提交时间起（yyyy-MM-dd）。 */
    private String adjustTimeStart;
    /** 提交时间止（yyyy-MM-dd）。 */
    private String adjustTimeEnd;
    /** 调整人（模糊搜索）。 */
    private String adjusterName;
    /** 调整方向：调入 / 调出。 */
    private String adjustMode;
    /** 审核状态码。 */
    private String auditStatus;
}

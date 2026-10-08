package com.znty.rrs.entity.batchfundpooladjust;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池批量调整分页查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchFundPoolAdjustReq extends PageRequest {
    /** 当前用户 ID */
    private String currentUserId;
    /** 筛选投资池 ID */
    private List<Long> poolIds;
    /** 目标投资池 ID */
    private Long poolId;
    /** 调整方向：in / out */
    private String direction;
    /** 基金代码 */
    private String fundCode;
    /** 基金简称 */
    private String fundShortName;
    /** 基金产品类型 */
    private String securityType;
    /** 基金管理人 */
    private String fundAdministrator;
}

package com.znty.rrs.entity.fundpooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池调整查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FundPoolAdjustReq extends PageRequest {
    /** 基金代码 */
    private String fundCode;
    /** 基金简称 */
    private String fundShortName;
    /** 基金产品类型 */
    private String securityType;
    /** 基金管理人 */
    private String fundAdministrator;
    /** 调库方向：in / out */
    private String adjustDirection;
    /** 当前用户 ID，1 为管理员 */
    private String currentUserId;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 调库记录 ID */
    private Long adjustLogId;
}

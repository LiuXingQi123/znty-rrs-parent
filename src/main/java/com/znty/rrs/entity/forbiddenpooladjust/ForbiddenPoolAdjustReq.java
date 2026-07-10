package com.znty.rrs.entity.forbiddenpooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 禁投池主体调整查询请求对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenPoolAdjustReq extends PageRequest {

    /** 主体代码 */
    private String companyCode;
    /** 主体简称 */
    private String companyShortName;
    /** 主体全称 */
    private String companyFullName;
    /** 所属行业 */
    private String industryName;
    /** 调整方向：in=调入 / out=调出 */
    private String adjustDirection;
    /** 当前用户 ID */
    private String currentUserId;
    /** 目标投资池 ID */
    private Long targetPoolId;
    /** 调库记录 ID */
    private Long adjustLogId;
    /** 调库批次号 */
    private String adjustBatchNo;
}

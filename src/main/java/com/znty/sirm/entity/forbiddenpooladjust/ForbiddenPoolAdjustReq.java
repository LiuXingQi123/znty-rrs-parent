package com.znty.sirm.entity.forbiddenpooladjust;

import com.znty.sirm.common.PageRequest;
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
    /** 发行人类型 */
    private String compType;
    /** 所属行业 */
    private String industryName;
    /** 主体评级 */
    private String companyRating;
    /** 主体内评分档 */
    private String companyInnerRating;
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

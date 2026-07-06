package com.znty.rrs.entity.securitypooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证券池调库请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityPoolAdjustReq extends PageRequest {
    /** 证券代码（模糊搜索） */
    private String securityCode;
    /** 证券简称（模糊搜索） */
    private String securityShortName;
    /** 发行人（模糊搜索） */
    private String issuer;
    /** 调库方向：in=可调入库 / out=可调出库 */
    private String adjustDirection;
    /** 当前用户 ID，1 视为管理员 */
    private String currentUserId;
    /** 目标投资池 ID（从具体投资池入口查看时使用） */
    private Long targetPoolId;
    /** 调库记录 ID（查询流程步骤时使用） */
    private Long adjustLogId;
    /** 调库批次号（查询同批次共用流程步骤时使用） */
    private String adjustBatchNo;
}

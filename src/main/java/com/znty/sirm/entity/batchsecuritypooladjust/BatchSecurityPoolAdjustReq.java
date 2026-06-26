package com.znty.sirm.entity.batchsecuritypooladjust;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 证券池批量调整查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchSecurityPoolAdjustReq extends PageRequest {

    /** 当前用户 ID，1001 视为管理员 */
    private String currentUserId;

    /** 投资池 ID 筛选列表 */
    private List<Long> poolIds;

    /** 目标投资池 ID */
    private Long poolId;

    /** 调整方向：in=调入 / out=调出 */
    private String direction;

    /** 证券代码（模糊） */
    private String securityCode;

    /** 证券简称（模糊） */
    private String securityShortName;

    /** 市场编码列表：SH / SZ / IB / BJ / NBC */
    private List<String> marketCodes;
}

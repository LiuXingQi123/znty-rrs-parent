package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 调整历史查询请求
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AdjustHistoryReq extends PageRequest {

    /** 投资池 ID 列表（树多选） */
    private List<Long> poolIds;

    /** 证券代码（模糊） */
    private String securityCode;

    /** 证券类型（精确） */
    private String securityType;

    /** 证券状态：存续 / 到期 */
    private String securityStatus;

    /** 调整日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 调整日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人名称（模糊） */
    private String adjusterName;

    /** 发行主体名称（模糊，联查 sirm_securityinfo） */
    private String issuer;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 审核状态：-1/00/10/11/20/21/99 */
    private String auditStatus;

    /** 仅查当前用户的调整记录 */
    private Boolean myBonds;

    /** 当前用户 ID（myBonds=true 时使用） */
    private String currentUserId;
}

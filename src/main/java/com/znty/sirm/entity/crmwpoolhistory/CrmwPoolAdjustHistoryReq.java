package com.znty.sirm.entity.crmwpoolhistory;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRMW池调整历史查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolAdjustHistoryReq extends PageRequest {

    /** CRMW代码（模糊搜索） */
    private String crmwScode;

    /** CRMW名称（模糊搜索） */
    private String crmwName;

    /** 调整日期起 */
    private String adjustTimeStart;

    /** 调整日期止 */
    private String adjustTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 调整状态 */
    private String auditStatus;
}

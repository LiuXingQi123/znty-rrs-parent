package com.znty.rrs.entity.crmwpoolhistory;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRMW池调整历史查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolAdjustHistoryReq extends PageRequest {

    /** CRMW银行间代码关键词 */
    private String crmwKeyword;
    /** CRMW凭证身份：正式ISIN / TEMP:临时代码 */
    private String crmwIsinCode;
    /** CRMW银行间代码快照，关联身份使用crmwIsinCode */
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

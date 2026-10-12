package com.znty.rrs.entity.crmwpoolquery;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRMW池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolQueryReq extends PageRequest {

    /** CRMW银行间代码关键词 */
    private String crmwKeyword;
    /** CRMW凭证身份：正式ISIN / TEMP:临时代码 */
    private String crmwIsinCode;
    /** CRMW银行间代码快照，关联身份使用crmwIsinCode */
    private String crmwScode;

    /** CRMW名称（模糊搜索） */
    private String crmwName;

    /** 入池时间起 */
    private String entryTimeStart;

    /** 入池时间止 */
    private String entryTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;
}

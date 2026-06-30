package com.znty.sirm.entity.crmwpoolquery;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRMW池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolQueryReq extends PageRequest {

    /** CRMW代码（模糊搜索） */
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

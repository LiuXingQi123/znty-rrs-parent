package com.znty.rrs.entity.forbiddenpoolhistory;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 禁投池历史查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenPoolHistoryReq extends PageRequest {

    /** 主体代码（模糊搜索） */
    private String companyCode;

    /** 主体名称（模糊搜索） */
    private String companyName;

    /** 证券代码（模糊搜索） */
    private String securityCode;

    /** 证券名称（模糊搜索） */
    private String securityShortName;

    /** 调整日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 调整日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 调整状态 */
    private String auditStatus;
}

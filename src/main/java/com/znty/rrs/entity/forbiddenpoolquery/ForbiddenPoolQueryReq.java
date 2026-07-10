package com.znty.rrs.entity.forbiddenpoolquery;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 禁投池分页查询请求对象，支持按证券代码、证券简称、类型、状态、调整人、入池日期等多条件过滤
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenPoolQueryReq extends PageRequest {

    /** 证券代码（模糊搜索） */
    private String securityCode;

    /** 证券简称（模糊搜索） */
    private String securityShortName;

    /** 证券类型（精确匹配） */
    private String securityType;

    /** 证券状态：active=存续 / matured=到期 */
    private String securityStatus;

    /** 入池日期起（yyyy-MM-dd） */
    private String entryTimeStart;

    /** 入池日期止（yyyy-MM-dd） */
    private String entryTimeEnd;

    /** 调整人名称（模糊搜索） */
    private String adjusterName;

    /** 发行主体名称（模糊搜索） */
    private String issuer;

}

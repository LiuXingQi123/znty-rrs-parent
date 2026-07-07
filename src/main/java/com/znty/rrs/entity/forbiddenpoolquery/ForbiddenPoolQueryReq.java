package com.znty.rrs.entity.forbiddenpoolquery;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 禁止交易池分页查询请求对象，支持按证券代码、证券简称、类型、调整人、日期等多条件过滤
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

    /** 证券状态：存续 / 到期 */
    private String securityStatus;

    /** 调整日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 调整日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人名称（模糊搜索） */
    private String adjusterName;

    /** 发行主体名称（模糊搜索） */
    private String issuer;

    /** 审核状态（调整状态）：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 99=发起人已撤回 */
    private String auditStatus;
}

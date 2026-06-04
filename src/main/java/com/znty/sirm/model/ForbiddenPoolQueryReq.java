package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 禁投池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenPoolQueryReq extends PageRequest {

    /** 证券代码（模糊搜索） */
    private String bondCode;

    /** 债券类型（精确匹配） */
    private String bondType;

    /** 债券状态：存续 / 到期 */
    private String bondStatus;

    /** 调整日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 调整日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人名称（模糊搜索） */
    private String adjusterName;

    /** 发行主体名称（模糊搜索） */
    private String issuer;

    /** 审核状态（调整状态）：00=待审核 10=审核中 11=已驳回 20=已生效 */
    private String auditStatus;
}

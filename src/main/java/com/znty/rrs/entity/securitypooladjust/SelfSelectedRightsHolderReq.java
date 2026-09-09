package com.znty.rrs.entity.securitypooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自选权益人分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SelfSelectedRightsHolderReq extends PageRequest {

    /** 主体编码 */
    private String companyCode;

    /** 主体名称 */
    private String companyName;
}

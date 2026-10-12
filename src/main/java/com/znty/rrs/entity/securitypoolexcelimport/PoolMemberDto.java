package com.znty.rrs.entity.securitypoolexcelimport;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 目标池当前有效在池成员（清空目标池用）
 */
@Data
public class PoolMemberDto {

    /** 证券与主体使用各自明确的关联键 */
    @JsonIgnore
    public String getObjectCode() { return "company".equals(securityType) ? securityCode : isinCode; }

    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
    /** 沪市证券代码 */
    private String windCodeSh;
    /** 深市证券代码 */
    private String windCodeSz;
    /** 银行间证券代码 */
    private String windCodeNib;
    /** 北交所证券代码 */
    private String windCodeBj;
    /** 其他市场证券代码 */
    private String windCodeNbc;
    /** 证券/主体代码 */
    private String securityCode;
    /** 简称 */
    private String securityShortName;
    /** 证券类型 */
    private String securityType;
}

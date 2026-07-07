package com.znty.rrs.entity.securitypooladjust;


import com.znty.rrs.entity.bo.SecurityInfoBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证券详情返回对象（调库页面顶部信息展示）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityInfoDetailDto extends SecurityInfoBo {

    /** 证券类型名称 */
    private String securityTypeName;

    /** 证券品种大类（bond/fund/stock/company，由 dict_security_type 推导，前端按类型差异化展示） */
    private String categoryType;
}

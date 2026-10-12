package com.znty.rrs.entity.crmwpooladjust;


import com.znty.rrs.entity.bo.SecurityInfoBo;
import lombok.Data;
import com.znty.rrs.entity.common.SecurityMarketDto;
import java.util.List;
import lombok.EqualsAndHashCode;

/**
 * 证券详情返回对象（调库页面顶部信息展示）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityInfoDetailDto extends SecurityInfoBo {

    /** 证券类型名称 */
    private String securityTypeName;

    /** ABS 普通权益人名称 */
    private String absOriginatorName;

    /** ABS 自选权益人名称 */
    private String companySelector;
    /** 当前代码所属市场。 */
    private String marketCode;
    /** 当前 Wind 同券关联代码，只读。 */
    private List<SecurityMarketDto> relatedMarketCodes;
}

package com.znty.rrs.entity.securitypooladjust;


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

    /** 当前在库或在途申请占用的 ABS 主体，全部出清后为空 */
    private SecurityInfoBo absIdentity;

    /** 证券类型名称 */
    private String securityTypeName;

    /**
     * 证券品种大类（由 dict_security_type 推导），用于前端按类型展示
     *
     * bond=债券、fund=基金、stock=股票、company=公司主体
     */
    private String categoryType;

    /** ABS 普通权益人名称（调库快照展示字段） */
    private String absOriginatorName;

    /** ABS 自选权益人名称（调库快照展示字段） */
    private String companySelector;

    /** 当前代码所属市场 */
    private String marketCode;

    /** 当前 Wind 同券关联代码，只读 */
    private List<SecurityMarketDto> relatedMarketCodes;
}

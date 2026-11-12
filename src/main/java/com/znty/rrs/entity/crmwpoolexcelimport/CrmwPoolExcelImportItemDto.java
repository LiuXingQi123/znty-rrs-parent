package com.znty.rrs.entity.crmwpoolexcelimport;

import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import lombok.Data;

import java.util.List;

/**
 * CRMW 池 Excel 导入明细展示
 */
@Data
public class CrmwPoolExcelImportItemDto {

    /** 明细主键 */
    private Long id;
    /** 明细业务号 */
    private String impDetlId;
    /** Excel 行号 */
    private Integer rowNo;
    /** CRMW 代码 */
    private String crmwScode;
    /** CRMW 全称 */
    private String crmwName;
    /** CRMW 市场编码（逗号分隔） */
    private String crmwMarket;
    /** CRMW 证券类型 */
    private String crmwStype;
    /** 证券代码 */
    private String securityCode;
    /** 证券全称 */
    private String securityName;
    /** 证券类型 code */
    private String securityType;
    /** 证券类型名称 */
    private String securityTypeName;
    /** 证券市场编码（逗号分隔） */
    private String securityMarket;
    /** 标的证券是否 ABS */
    private Integer absFlag;
    /** 已保存的关联担保人或权益人代码 */
    private String relatedCompanyCode;
    /** 已保存的 ABS 自选权益人代码 */
    private String selfSelectedRightsHolderCode;
    /** 标的证券的关联主体候选 */
    private List<RelatedRatingCompanyDto> relatedRatingCompanies;
    /** 已保存的自选权益人及其评级 */
    private SelfSelectedRightsHolderDto selfSelectedRightsHolder;
    /** 解析后的目标池 ID */
    private Long resolvedPoolId;
    /** 校验结果 0/1/2 */
    private String chkRslt;
    /** 校验说明 */
    private String chkDscr;
    /** 保存结果 */
    private String saveRslt;
    /** 保存说明 */
    private String saveDscr;
    /** 业务单 ID */
    private Long refId;
}

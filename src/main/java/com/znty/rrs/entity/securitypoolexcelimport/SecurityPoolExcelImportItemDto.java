package com.znty.rrs.entity.securitypoolexcelimport;

import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import lombok.Data;

import java.util.List;

/**
 * 证券/主体 Excel 导入明细展示（对齐模板列）
 */
@Data
public class SecurityPoolExcelImportItemDto {

    /** 明细主键 */
    private Long id;
    /** 明细业务号 */
    private String impDetlId;
    /** Excel 行号 */
    private Integer rowNo;
    /** 父池名称 */
    private String parentPoolName;
    /** 子池名称 */
    private String childPoolName;
    /** 证券/主体名称 */
    private String securityName;
    /** 证券/主体代码 */
    private String securityCode;
    /** 本次采用的权益人/担保人主体代码 */
    private String ratingCompanyCode;
    /** 本次采用的权益人/担保人名称 */
    private String ratingCompanyName;
    /** 证券类型 */
    private String securityType;
    /** 是否 ABS，来自证券主数据 */
    private Integer absFlag;
    /** 当前证券的四类关联主体候选 */
    private List<RelatedRatingCompanyDto> relatedRatingCompanies;
    /** 页面选中的关联主体代码 */
    private String relatedCompanyCode;
    /** 页面选中的关联主体名称 */
    private String relatedCompanyName;
    /** 页面选中的 ABS 自选权益人代码 */
    private String selfSelectedRightsHolderCode;
    /** 页面选中的 ABS 自选权益人及其最新内评 */
    private SelfSelectedRightsHolderDto selfSelectedRightsHolder;
    /** 最终采用主体的最新内评 */
    private String ratingCompanyInnerRating;
    /** 市场类型 */
    private String marketType;
    /** 证券品种 */
    private String securityVariety;
    /** 调整人（Excel 填报） */
    private String excelAdjuster;
    /** 调整时间（Excel 填报） */
    private String excelAdjustTime;
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

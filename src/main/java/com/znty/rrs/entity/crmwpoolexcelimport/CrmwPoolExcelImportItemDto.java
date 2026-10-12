package com.znty.rrs.entity.crmwpoolexcelimport;

import lombok.Data;

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
    /** CRMW凭证身份：正式ISIN / TEMP:临时代码 */
    private String crmwIsinCode;
    /** CRMW银行间代码快照，关联身份使用crmwIsinCode */
    private String crmwScode;
    /** CRMW 全称 */
    private String crmwName;
    /** CRMW 市场编码（逗号分隔） */
    private String crmwMarket;
    /** CRMW 证券类型 */
    private String crmwStype;
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
    /** 证券来源：official=正式 / temporary=临时 / temp_converted=已转正 */
    private String securitySource;
    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
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

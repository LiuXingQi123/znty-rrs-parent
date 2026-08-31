package com.znty.rrs.entity.schedule;

import lombok.Data;

/** 恒生池 Excel 导出原始行。 */
@Data
public class HsPoolExportRowDto {
    /** 证券简称。 */
    private String securityShortName;
    /** 沪市证券代码。 */
    private String windCodeSh;
    /** 深市证券代码。 */
    private String windCodeSz;
    /** 银行间市场证券代码。 */
    private String windCodeNib;
    /** 北交所证券代码。 */
    private String windCodeBj;
    /** 其他市场证券代码。 */
    private String windCodeNbc;
    /** 操作类型，调出时为“删除”，调入和全量时为空。 */
    private String operationType;
}

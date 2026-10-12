package com.znty.rrs.entity.schedule;

import lombok.Data;

/** 恒生池 Excel 导出原始行。 */
@Data
public class HsPoolExportRowDto {
    /** 证券简称。 */
    private String securityShortName;
    /** 实际在池记录或日志对应的代码。 */
    private String securityCode;
    /** 当前代码所属市场。 */
    private String marketCode;
    /** 操作类型，调出时为“删除”，调入和全量时为空。 */
    private String operationType;
}

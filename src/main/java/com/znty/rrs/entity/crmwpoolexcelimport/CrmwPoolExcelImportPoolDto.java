package com.znty.rrs.entity.crmwpoolexcelimport;

import lombok.Data;

/**
 * CRMW 池 Excel 导入可选目标池
 */
@Data
public class CrmwPoolExcelImportPoolDto {

    /** 投资池 ID */
    private Long id;
    /** 投资池名称 */
    private String poolName;
    /** 投资池类型 */
    private String poolType;
}

package com.znty.rrs.entity.schedule;

import lombok.Data;

/** 恒生池 Excel 导出的叶子投资池。 */
@Data
public class HsPoolExportPoolDto {
    /** 投资池 ID。 */
    private Long poolId;
    /** 投资池名称。 */
    private String poolName;
    /** 投资池完整层级名称，恒生池名称为空时作为 Sheet 名称。 */
    private String poolFullName;
    /** 恒生池名称，支持使用竖线配置多个 Sheet 名称。 */
    private String hsPoolName;
}

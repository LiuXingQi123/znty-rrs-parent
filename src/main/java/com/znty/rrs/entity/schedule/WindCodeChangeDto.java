package com.znty.rrs.entity.schedule;

import lombok.Data;

/** Wind 债券代码变更事件，字段来自外部 ODS 表。 */
@Data
public class WindCodeChangeDto {
    /** 外部事件对象 ID。 */
    private String objectId;
    /** 唯一证券 ID。 */
    private String secId;
    /** 这条历史记录中的 Wind 证券代码，可能与其他变更记录重复，不能作为记录唯一标识。 */
    private String windCode;
    /** 变更前代码。 */
    private String oldWindCode;
    /** 变更后代码。 */
    private String newWindCode;
    /** 八位变更日期，yyyyMMdd。 */
    private String changeDate;
}

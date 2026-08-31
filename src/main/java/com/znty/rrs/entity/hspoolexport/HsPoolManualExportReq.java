package com.znty.rrs.entity.hspoolexport;

import lombok.Data;

import java.util.List;

/** 恒生格式手动导出请求。 */
@Data
public class HsPoolManualExportReq {
    /** 指定投资池 ID；为空时导出全部叶子池。 */
    private List<Long> poolIds;
    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String startTime;
    /** 结束时间，格式 yyyy-MM-dd HH:mm:ss；为空时使用当前时间。 */
    private String endTime;
    /** 导出模式：increment=增量 / full=全量。 */
    private String exportMode;
}

package com.znty.rrs.entity.report;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告库分页查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportReq extends PageRequest {

    /** 报告 ID，详情查询必填 */
    private Long reportId;

    /** 报告来源：internal / external，详情查询必填 */
    private String source;

    /** 报告标题关键字 */
    private String reportTitle;

    /** 证券编码关键字 */
    private String securityCode;

    /** 报告类型 */
    private String reportType;

    /** 创建时间起（yyyy-MM-dd） */
    private String crteTimeStart;

    /** 创建时间止（yyyy-MM-dd） */
    private String crteTimeEnd;
}

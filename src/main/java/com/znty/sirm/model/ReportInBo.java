package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 内部报告库表实体，对应 sirm_report_in 表
 */
@Data
public class ReportInBo {

    /** 主键 ID */
    private Long id;

    /** 作者姓名 */
    private String authorName;

    /** 报告标题 */
    private String reportTitle;

    /** 报告类型：bond_in_report=债券入库报告 / bond_out_report=债券出库报告 / fund_in_report=基金入库报告 / fund_out_report=基金出库报告 / stock_in_report=股票入库报告 / stock_out_report=股票出库报告 / other_report=其他报告 */
    private String reportType;

    /** 证券编码 */
    private String securityCode;

    /** 主体编码 */
    private String companyCode;

    /** 证券类型：bond=债券 / fund=基金 / stock=股票 / company=主体 / other=其他 */
    private String securityType;

    /** 数据来源：migrated=迁移数据 / uploaded=系统上传数据 */
    private String dataSource;

    /** 逻辑删除标志：0=正常 / 1=已删除 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

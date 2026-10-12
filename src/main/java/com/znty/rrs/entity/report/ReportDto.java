package com.znty.rrs.entity.report;


import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;
import java.util.Date;

/**
 * 报告库查询结果 DTO
 */
@Data
public class ReportDto {

    /** 主键 ID */
    private Long id;

    /** 作者姓名 */
    private String authorName;

    /** 报告标题 */
    private String reportTitle;

    /** 报告类型编码 */
    private String reportType;

    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
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
    /** 证券编码 */
    private String securityCode;

    /** 主体编码 */
    private String companyCode;

    /** 证券类型编码 */
    private String securityType;

    /** 来源机构名称 */
    private String sourceOrgName;

    /** 数据来源编码 */
    private String dataSource;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 报告附件列表 */
    private List<SysAttachmentDto> attachments;
}

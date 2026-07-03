package com.znty.rrs.entity.forbiddenpoolquery;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 禁止交易池查询结果 DTO，返回当前处于禁投池中的证券信息及入池时间
 */
@Data
public class ForbiddenPoolQueryDto {

    /** 主键 ID */
    private Long id;

    /** 证券简称 */
    private String securityShortName;

    /** 证券代码 */
    private String securityCode;

    /** 发行主体 */
    private String issuer;

    /** 调整人 */
    private String adjusterName;

    /** 证券类型编码 */
    private String securityType;

    /** 证券类型名称（关联 dict_security_type 表） */
    private String securityTypeName;

    /** 证券类型分类（company=公司主体） */
    private String categoryType;

    /** 投资池名称 */
    private String targetPoolName;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;

    /** 到期日（前端用于派生证券状态：存续/到期） */
    private String maturityDate;

    /** 退市日期 */
    private String delistDate;

    /** 行权日期（回售） */
    private String repurchaseDate;
}

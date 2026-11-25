package com.znty.rrs.entity.forbiddenpoolquery;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 禁投池查询结果 DTO，返回当前处于禁投池中的证券信息及入池时间
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

    /** 统一社会信用代码：主体行为自身信用代码，债券行为其发行主体信用代码 */
    private String registerNumber;

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

    /** 来源调库日志 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;

    /** 证券主档状态，D 为到期 */
    private String securityStatus;

    /** 到期日，格式 yyyyMMdd */
    private String maturityDate;

    /** 摘牌日（业务到期日，格式 yyyyMMdd） */
    private String delistDate;

    /** 行权日期（回售） */
    private String repurchaseDate;
}

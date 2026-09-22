package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/**
 * 证券代码转换业务对象。
 * <p>仅供独立证券代码转换流程读取主档、迁移运行态引用和写入转换日志。</p>
 */
@Data
public class SecurityCodeConversionBo {

    /** 记录主键 */
    private Long id;

    /** 临时证券名称 */
    private String tempSecurityName;

    /** 临时证券代码 */
    private String tempSecurityCode;

    /** 临时证券市场 */
    private String tempSecurityMarket;

    /** 临时证券类型 */
    private String tempSecurityType;

    /** 正式证券名称 */
    private String securityName;

    /** 正式证券代码或池状态证券代码 */
    private String securityCode;

    /** 正式证券市场 */
    private String securityMarket;

    /** 正式证券类型或池状态证券类型 */
    private String securityType;

    /** 操作来源 */
    private String oprtSource;

    /** 替换表名 */
    private String replaceTableName;

    /** 被替换记录主键 */
    private Long replaceRecordId;

    /** 替换状态 */
    private String replaceStatus;

    /** 统一更新时间 */
    private Date updateTime;

    /** 主档关联代码 */
    private String windCode;

    /** 主档证券全称 */
    private String fullName;

    /** 主档证券简称 */
    private String shortName;

    /** 主档沪市证券代码 */
    private String windCodeSh;

    /** 主档深市证券代码 */
    private String windCodeSz;

    /** 主档银行间市场代码 */
    private String windCodeNib;

    /** 主档北交所代码 */
    private String windCodeBj;

    /** 主档其他市场代码 */
    private String windCodeNbc;

    /** 池状态证券简称 */
    private String securityShortName;

    /** CRMW 名称 */
    private String crmwName;

    /** CRMW 证券代码 */
    private String crmwScode;

    /** CRMW 市场代码 */
    private String crmwMktcode;

    /** CRMW 证券类型 */
    private String crmwStype;

    /** 调整类型 */
    private String adjustType;

    /** 调整模式 */
    private String adjustMode;

    /** 调整批次号 */
    private String adjustBatchNo;

    /** 调库日志主键 */
    private Long adjustLogId;

    /** 目标池主键 */
    private Long targetPoolId;

    /** 目标池名称 */
    private String targetPoolName;

    /** 目标池类型 */
    private String poolType;

    /** 审批状态 */
    private String auditStatus;

    /** 调整人主键 */
    private String adjusterId;

    /** 调整人名称 */
    private String adjusterName;

    /** 调整原因 */
    private String adjustReason;

    /** 调整建议 */
    private String adjustAdvice;

    /** 提交时间 */
    private Date submitTime;

    /** 审批时间 */
    private Date auditTime;

    /** 入池时间 */
    private Date entryTime;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    private Date crteTime;

    /** 修改时间 */
    private Date updtTime;
}

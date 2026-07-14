package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/**
 * 临时代码业务对象，对应 rrs_temp_security_code 表
 */
@Data
public class TempSecurityCodeBo {

    /** 主键 ID */
    private Long id;
    /** 临时证券名称 */
    private String tempSecurityName;
    /** 临时证券代码 */
    private String tempSecurityCode;
    /** 临时证券市场 */
    private String tempSecurityMarket;
    /** 临时证券类型 */
    private String tempSecurityType;
    /** 临时缓释凭证代码 */
    private String tempMitigationCode;
    /** 临时关联主体代码（wind_cbondissuer.s_info_compcode） */
    private String tempCompanyCode;
    /** 临时关联主体名称快照 */
    private String tempCompanyNameSnapshot;
    /** 临时发行日期 */
    private Date tempIssueDate;
    /** 临时到期日期 */
    private Date tempMaturityDate;
    /** 正式证券名称 */
    private String securityName;
    /** 正式证券代码 */
    private String securityCode;
    /** 正式证券市场 */
    private String securityMarket;
    /** 正式证券类型 */
    private String securityType;
    /** 证券来源 */
    private String securitySource;
    /** 业务更新时间 */
    private Date updateTime;
    /** 状态 */
    private String status;
    /** 最近操作 */
    private String operationType;
    /** 是否删除 */
    private Integer isDeleted;
    /** 创建时间 */
    private Date crteTime;
    /** 更新时间 */
    private Date updtTime;
    /** 替换日志表名 */
    private String replaceTableName;
    /** 替换日志记录 ID */
    private Long replaceRecordId;
    /** 替换状态 */
    private String replaceStatus;
    /** 取消发行日期 */
    private String cancelDate;
}

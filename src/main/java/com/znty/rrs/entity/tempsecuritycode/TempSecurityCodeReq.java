package com.znty.rrs.entity.tempsecuritycode;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.znty.rrs.common.PageRequest;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 临时代码管理请求对象，承载查询条件、新增参数和状态操作参数
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TempSecurityCodeReq extends PageRequest {

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
    /** 临时关联主体 ID */
    private Long tempCompanyId;
    /** 临时发行日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date tempIssueDate;
    /** 临时到期日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date tempMaturityDate;
    /** 正式证券名称 */
    private String securityName;
    /** 正式证券代码 */
    private String securityCode;
    /** 正式证券市场 */
    private String securityMarket;
    /** 正式证券类型 */
    private String securityType;
    /** 操作人 ID */
    private String operatorId;
}

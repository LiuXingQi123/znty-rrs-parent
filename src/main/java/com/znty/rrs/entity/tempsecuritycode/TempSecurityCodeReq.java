package com.znty.rrs.entity.tempsecuritycode;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.znty.rrs.common.PageRequest;
import java.util.Date;
import java.util.List;
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

    /**
     * 状态列表（多选）
     *
     * temporary=临时、updated=已更新、cancelled=已取消
     */
    private List<String> statusList;

    /**
     * 操作来源列表（多选）
     *
     * manual=人工、job=定时任务、other=其他
     */
    private List<String> oprtSourceList;

    /** 临时证券市场 */
    private String tempSecurityMarket;

    /** 临时证券类型 */
    private String tempSecurityType;

    /** 临时缓释凭证代码 */
    private String tempMitigationCode;

    /** 临时关联主体代码（wind_compintroduction.COMP_ID） */
    private String tempCompanyCode;

    /** 发行主体远程搜索关键字（名称、代码模糊，最多返回 50 条） */
    private String companyKeyword;

    /** 正式证券远程搜索关键字（代码、名称模糊，最多返回 50 条） */
    private String securityKeyword;

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

    /**
     * 发行主体弹窗查询条件
     */
    @Data
    public static class IssuerQueryReq {

        /** 主体代码前缀 */
        private String companyCode;

        /** 主体全称片段 */
        private String companyFullName;

        /** 统一社会信用代码前缀 */
        private String registerNumber;
    }
}

package com.znty.rrs.entity.tempsecuritycode;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 临时代码管理返回对象，承载列表记录、主体选项和证券类型选项
 */
@Data
public class TempSecurityCodeDto {

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
    /** 临时证券类型名称 */
    private String tempSecurityTypeName;
    /** 临时缓释凭证代码 */
    private String tempMitigationCode;
    /** 临时关联主体代码 */
    private String tempCompanyCode;
    /** 临时关联主体名称快照 */
    private String tempCompanyNameSnapshot;
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
    /** 正式证券类型名称 */
    private String securityTypeName;
    /** 业务更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    /** 状态 */
    private String status;
    /** 最近操作 */
    private String operationType;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;

    /**
     * 发行主体下拉选项
     */
    @Data
    public static class CompanyOption {

        /** 主体编码（s_info_compcode） */
        private String companyCode;
        /** 主体全称 */
        private String fullName;
        /** 主体简称 */
        private String shortName;
    }

    /**
     * 证券类型下拉选项
     */
    @Data
    public static class SecurityTypeOption {

        /** 证券类型编码 */
        private String securityType;
        /** 证券类型名称 */
        private String securityTypeName;
        /** 大类编码 */
        private String categoryType;
        /** 大类名称 */
        private String categoryTypeName;
    }

    /**
     * 下拉选项聚合对象
     */
    @Data
    public static class OptionBundle {

        /** 发行主体选项 */
        private List<CompanyOption> companies = new ArrayList<>();
        /** 证券类型选项 */
        private List<SecurityTypeOption> securityTypes = new ArrayList<>();
    }
}

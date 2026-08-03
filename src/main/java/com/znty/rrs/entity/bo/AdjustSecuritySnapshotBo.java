package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 调库证券信息快照表实体，对应 ip_adjust_security_snapshot
 */
@Data
public class AdjustSecuritySnapshotBo {

    /** 主键 ID */
    private Long id;
    /** 关联调库日志 ID */
    private Long adjustLogId;
    /** 填写人 ID */
    private String submitterId;
    /** 本条快照提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    /** 关联代码 */
    private String windCode;
    /** 证券全称 */
    private String fullName;
    /** 证券简称 */
    private String shortName;
    /** 发行人 */
    private String issuer;
    /** 发行人代码 */
    private String issuerCode;
    /** 银行间市场代码 */
    private String windCodeNib;
    /** 沪市证券代码 */
    private String windCodeSh;
    /** 深市证券代码 */
    private String windCodeSz;
    /** 北交所代码 */
    private String windCodeBj;
    /** 发行规模(亿元) */
    private BigDecimal issueAmountplan;
    /** 票面利率(%) */
    private String couponRate;
    /** 含权债剩余期限-最新 */
    private BigDecimal dateInrightExists;
    /** 起息日期 */
    private String carryDate;
    /** 到期日期 */
    private String maturityDate;
    /** 质押比率(%) */
    private BigDecimal infoPledgeRatio;
    /** 债券评级机构 */
    private String ratingBondAgency;
    /** 债券评级 */
    private String ratingBond;
    /** 主体评级 */
    private String ratingBondissuer;
    /** 展望评级 */
    private String ratingOutlook;
    /** 担保人 */
    private String guarantor;
    /** 担保人ID */
    private String guarantorId;
    /** 主承销商 */
    private String agencyName;
    /** 主体内评分档 */
    private String innerIssuerRating;
    /** 证券类型编码 */
    private String securityType;
    /** 品种类别 */
    private String secTypename;
    /** 赎回剩余期限-最新 */
    private BigDecimal dateCallExists;
    /** 担保人主体内评分 */
    private String innerGuarantorRating;
    /** 剩余期限-最新（天） */
    private BigDecimal dateExists;
    /** 资金募集用途 */
    private String fundUse;
    /** 提示原因 */
    private String promptReason;
    /** 证券分析 */
    private String analysis;

    /** 其他市场代码 */
    private String windCodeNbc;
    /** 证券期限(年) */
    private BigDecimal termYear;
    /** 证券期限(天) */
    private BigDecimal termDay;
    /** 含权期限说明 */
    private String maturityembeddedDesc;
    /** 发行人类型 */
    private String compType;
    /** 发行总额-亿 */
    private BigDecimal issueAmountact;
    /** 回购剩余期限-最新 */
    private BigDecimal dateRepurchaseExists;
    /** 是否 ABS */
    private Integer absFlag;
    /** 是否担保 */
    private Integer guarantFlag;
    /** 担保类型 */
    private String guarantType;
    /** 主体评级机构 */
    private String ratingBondissuerAgency;
    /** 主承销商Id */
    private String agencyNameid;

    /** 债项业务细类 */
    private Integer bondBizType;
    /** ABS 相关主体/权益人名称 */
    private String absOriginatorName;
    /** ABS 分层比例 */
    private BigDecimal absShareRatio;
    /** 预期到期日 */
    private String expectedMaturityDate;
    /** 法定到期日 */
    private String legalMaturityDate;
    /** 中介/报告机构 */
    private String absReportOrg;
    /** 托管机构 */
    private String absCustodian;
    /** 主体选择器展示值 */
    private String companySelector;
    /** 扩展指标1 */
    private String extraInd1;
    /** 扩展指标2 */
    private String extraInd2;
    /** 扩展指标3 */
    private String extraInd3;

    /** 逻辑删除标志：0=正常 / 1=已删除 */
    private Integer isDeleted;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

package com.znty.rrs.entity.batchcrmwpooladjust;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CRMW 池批量调整候选组合返回对象
 */
@Data
public class BatchCrmwCandidateDto {

    /** 标的证券代码 */
    private String securityCode;

    /** 标的证券简称 */
    private String securityShortName;

    /** 标的证券类型 */
    private String securityType;

    /** 标的证券是否 ABS：1=是 / 0=否 */
    private Integer absFlag;

    /** 标的证券是否担保：1=是 / 0=否 */
    private Integer guarantFlag;

    /** 标的证券是否永续：1=是 / 0=否 */
    private Integer yxFlag;

    /** 标的证券是否次级：1=是 / 0=否 */
    private Integer cjFlag;

    /** 标的证券发行方式 */
    private String issueType;

    /** 标的证券内部分类 */
    private String innerClass;

    /** 标的证券是否含权：1=是 / 0=否 */
    private Integer inrightFlag;

    /**
     * 市场编码列表：
     * SSE=上海证券交易所 / SZSE=深圳证券交易所 / CIBM=银行间市场 / BSE=北京证券交易所 /
     * COMPANY=主体 / OTC=场外市场 / QDII=其他QDII市场 / JWCW=JWCW市场 / UNKNOWN=未知 / OTHER=其他
     */
    private List<String> marketCodes = new ArrayList<>();

    /** SQL 查询返回的市场编码文本 */
    @JsonIgnore
    private String marketCodeText;

    /** CRMW 凭证名称 */
    private String crmwName;

    /** CRMW 凭证代码 */
    private String crmwScode;

    /** CRMW 证券类型 */
    private String crmwStype;

    /** 发行人 */
    private String issuer;

    /** 证券评级 */
    private String ratingBond;

    /** 主体评级 */
    private String ratingBondissuer;

    /** 主体内评分档 */
    private String innerIssuerRating;

    /** 担保人（名称，多担保人逗号分隔） */
    private String guarantor;

    /** 担保人ID（代码，多担保人逗号分隔，与 guarantor 按位置配对） */
    private String guarantorId;

    /** 到期日期 */
    private String maturityDate;

    /** 剩余期限（天），对应 rrs_securityinfo.date_exists；列表前端 ÷365 展示为年 */
    private BigDecimal dateExists;
}

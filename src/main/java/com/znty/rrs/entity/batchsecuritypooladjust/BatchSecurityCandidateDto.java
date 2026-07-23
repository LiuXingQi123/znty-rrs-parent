package com.znty.rrs.entity.batchsecuritypooladjust;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 证券池批量调整候选证券返回对象
 */
@Data
public class BatchSecurityCandidateDto {

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /**
     * 市场编码列表：
     * SSE=上海证券交易所 / SZSE=深圳证券交易所 / CIBM=银行间市场 / BSE=北京证券交易所 /
     * COMPANY=主体 / OTC=场外市场 / QDII=其他QDII市场 / OTHER=其他
     */
    private List<String> marketCodes = new ArrayList<>();

    /** SQL 查询返回的市场编码文本 */
    @JsonIgnore
    private String marketCodeText;

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
    private java.math.BigDecimal dateExists;
}

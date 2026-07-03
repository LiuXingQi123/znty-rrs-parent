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

    /** 市场编码列表 */
    private List<String> marketCodes = new ArrayList<>();

    /** SQL 查询返回的市场编码文本 */
    @JsonIgnore
    private String marketCodeText;

    /** 发行人 */
    private String issuer;

    /** 证券评级 */
    private String ratingBond;

    /** 到期日期 */
    private String maturityDate;
}

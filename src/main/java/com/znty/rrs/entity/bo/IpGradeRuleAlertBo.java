package com.znty.rrs.entity.bo;

import lombok.Data;

import java.util.Date;

/**
 * 不符合主体债入池规则提醒待办
 */
@Data
public class IpGradeRuleAlertBo {

    /** 主键 ID */
    private Long id;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 发行主体代码 */
    private String issuerCode;

    /** 发行主体名称 */
    private String issuerName;

    /** 当前所在投资池 ID */
    private Long currentPoolId;

    /** 当前所在投资池名称 */
    private String currentPoolName;

    /** 当前分级库档位 */
    private Integer currentSort;

    /** 不符合原因 */
    private String failReason;

    /** 命中的特殊类型说明 */
    private String specialTypeDesc;

    /** 规则类型：grade=分级规则 / absCompanySync=ABS主体联动 */
    private String ruleType;

    /** 本次主体身份和池状态依据摘要 */
    private String contextKey;

    /** 待办状态：00=待处理 / 20=已处理 / 99=已失效 */
    private String alertStatus;

    /** 最近一次扫描命中时间 */
    private Date lastScanTime;

    /** 处理人 ID */
    private String dealUserId;

    /** 处理人名称 */
    private String dealUserName;

    /** 处理时间 */
    private Date dealTime;

    /** 逻辑删除 */
    private Integer isDeleted;

    /** 创建时间 */
    private Date crteTime;

    /** 修改时间 */
    private Date updtTime;
}

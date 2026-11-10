package com.znty.rrs.entity.bo;

import lombok.Data;

import java.util.Date;

/**
 * CRMW 在库规则提醒待办
 *
 * 业务示例：
 * 1. 标的债券：A 公司发行的债券
 * 2. CRMW 凭证：由 B 机构创设／提供信用保护
 * 3. 本表创设方／保护方及其内评字段对应 B 机构，标的证券字段对应 A 公司发行的债券
 */
@Data
public class IpCrmwRuleAlertBo {

    /** 主键 ID */
    private Long id;
    /** CRMW 凭证代码，示例中 B 机构创设的凭证 */
    private String crmwScode;
    /** CRMW 凭证名称 */
    private String crmwName;
    /** 标的证券代码，示例中 A 公司发行的债券 */
    private String securityCode;
    /** 标的证券简称 */
    private String securityShortName;
    /** 创设方或保护方代码，示例中的 B 机构，取凭证主档发行主体 */
    private String issuerCode;
    /** 创设方或保护方名称 */
    private String issuerName;
    /** 创设方或保护方最新主体内评，示例中 B 机构的内评 */
    private String innerIssuerRating;
    /** 当前所在投资池 ID */
    private Long currentPoolId;
    /** 当前所在投资池名称 */
    private String currentPoolName;
    /** 本次在池记录的来源调库日志 ID */
    private Long adjustLogId;
    /** 命中的规则原因 */
    private String failReason;
    /** 提醒依据摘要，相同依据沿用人工处理结论 */
    private String contextKey;
    /** 提醒状态：00=待处理 / 20=已处理 / 99=已失效 */
    private String alertStatus;
    /** 最近一次命中时间 */
    private Date lastScanTime;
    /** 处理人 ID */
    private String dealUserId;
    /** 处理人名称 */
    private String dealUserName;
    /** 处理时间 */
    private Date dealTime;
    /** 逻辑删除：0=正常 / 1=已删除 */
    private Integer isDeleted;
    /** 创建时间 */
    private Date crteTime;
    /** 修改时间 */
    private Date updtTime;
}

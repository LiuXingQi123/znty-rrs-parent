package com.znty.rrs.entity.bo;

import lombok.Data;

import java.util.Date;

/**
 * 不符合主体债入库规则提醒待办
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

    /** 当前所在分级库 ID */
    private Long currentPoolId;

    /** 当前所在分级库名称 */
    private String currentPoolName;

    /** 当前分级库档位 */
    private Integer currentSort;

    /** 不符合原因 */
    private String failReason;

    /** 命中的特殊类型说明 */
    private String specialTypeDesc;

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

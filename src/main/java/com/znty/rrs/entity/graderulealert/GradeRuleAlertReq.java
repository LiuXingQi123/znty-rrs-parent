package com.znty.rrs.entity.graderulealert;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 不符合分级规则提醒查询 / 处理入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GradeRuleAlertReq extends PageRequest {

    /** 待办主键 */
    private Long id;

    /** 证券代码（模糊） */
    private String securityCode;

    /** 待办状态：00=待处理 / 20=已处理 / 99=已失效 */
    private String alertStatus;

    /** 规则类型：grade=分级规则 / absCompanySync=ABS主体联动 */
    private String ruleType;

    /** 处理人 ID */
    private String currentUserId;

    /** 处理人名称 */
    private String currentUserName;

    /** ABS 处理动作：confirm=确认主体 / noNeed=无需入库或无需处理 */
    private String decision;

    /** ABS 确认主体代码 */
    private String issuerCode;

    /** ABS 确认主体名称 */
    private String issuerName;

    /** ABS 主体外评 */
    private String ratingBondissuer;

    /** ABS 主体外评机构 */
    private String ratingBondissuerAgency;

    /** ABS 主体内评分 */
    private String innerIssuerRating;
}

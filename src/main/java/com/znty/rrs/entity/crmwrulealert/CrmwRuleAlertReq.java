package com.znty.rrs.entity.crmwrulealert;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRMW 规则提醒查询及处理请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwRuleAlertReq extends PageRequest {

    /** 提醒主键 ID */
    private Long id;
    /** CRMW 凭证代码，模糊查询 */
    private String crmwScode;
    /** 标的证券代码，模糊查询 */
    private String securityCode;
    /** 提醒状态：00=待处理 / 20=已处理 / 99=已失效 */
    private String alertStatus;
    /** 当前处理人 ID */
    private String currentUserId;
    /** 当前处理人名称 */
    private String currentUserName;
}

package com.znty.rrs.entity.schedule;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRMW 在池组合及创设方当前风险状态 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwRuleAlertScanDto extends IpCrmwRuleAlertBo {

    /** 凭证主档证券类型，用于发现缺失或错误的凭证数据 */
    private String crmwSecurityType;
    /** 创设方或保护方是否在观察池 */
    private boolean inObservePool;
    /** 创设方或保护方是否在重点观察名单 */
    private boolean inRestrictedPool;
}

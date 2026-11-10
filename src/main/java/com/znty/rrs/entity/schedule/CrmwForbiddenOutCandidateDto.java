package com.znty.rrs.entity.schedule;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRMW关联禁投出库候选，沿用组合身份并携带本次扫描的池状态主键 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwForbiddenOutCandidateDto extends IpAdjustLogBo {

    /** 原组合池状态主键，只退出本次扫描的记录，不影响后续重新入库的记录 */
    private Long poolStatusId;
}

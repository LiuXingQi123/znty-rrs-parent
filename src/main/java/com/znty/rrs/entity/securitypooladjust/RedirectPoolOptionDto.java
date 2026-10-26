package com.znty.rrs.entity.securitypooladjust;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批改判候选池，完整路径仅用于展示，保留原池名称用于落库 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RedirectPoolOptionDto extends InvestmentPoolBo {

    /** 投资池全路径名称 */
    private String poolFullName;
}

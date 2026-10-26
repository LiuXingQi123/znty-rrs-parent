package com.znty.rrs.entity.graderulealert;

import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分级规则扫描记录，携带当前在池状态对应的入库日志 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GradeRuleAlertScanDto extends IpGradeRuleAlertBo {

    /** 当前在池状态的来源调库日志 ID，仅供扫描定位入库快照 */
    private Long adjustLogId;
}

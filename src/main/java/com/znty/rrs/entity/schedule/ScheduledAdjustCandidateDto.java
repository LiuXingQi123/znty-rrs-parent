package com.znty.rrs.entity.schedule;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 定时任务自动调库候选记录，承载日志字段及规则判断辅助信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduledAdjustCandidateDto extends IpAdjustLogBo {

    /** 发行主体代码 */
    private String issuerCode;

    /** 发行主体名称 */
    private String issuerName;

    /** 到期日，格式 yyyyMMdd */
    private String maturityDate;

    /** 近一年认可外评孰低值 */
    private String outerRating;

    /** 当前是否在公司信用债禁止库：1=是 */
    private Integer inForbiddenPool;

    /** 当前是否在重点观察名单：1=是 */
    private Integer inRestrictedPool;

    /** 近一年认可外评孰低是否为 AA-及以下：1=是 */
    private Integer inLowOuterRating;
}

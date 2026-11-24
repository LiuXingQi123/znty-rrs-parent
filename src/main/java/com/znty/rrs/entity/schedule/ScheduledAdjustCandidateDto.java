package com.znty.rrs.entity.schedule;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 定时任务自动调库候选记录，承载日志字段及规则判断辅助信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduledAdjustCandidateDto extends IpAdjustLogBo {

    /** 本次独立同步可撤销的在池记录 ID */
    private Long poolStatusId;

    /** ABS 标志，用于自动调库共用主体身份校验 */
    private Integer absFlag;

    /** 实际触发联动的主体，与 ABS 唯一身份分开保存 */
    private String triggerCompanyCode;

    /** 债券跟随调整是否来自已经生效的主体 Excel 决定 */
    private boolean excelCompanyAdjustment;

    /** 债券退市或状态缺失，仅在主体 Excel 调出时允许撤销跟随记录 */
    private boolean inactiveForOrdinaryOut;

    /** 发行主体代码 */
    private String issuerCode;

    /** 发行主体名称 */
    private String issuerName;

    /** 摘牌日（业务到期日，格式 yyyyMMdd） */
    private String delistDate;

    /** 到期出池候选是否已在任一调出限制池中 */
    private boolean outRestricted;

    /** 近一年认可外评孰低值 */
    private String outerRating;

    /** 当前是否在公司信用债禁止库：1=是 */
    private Integer inForbiddenPool;

    /** 当前是否在重点观察名单：1=是 */
    private Integer inRestrictedPool;

    /** 近一年认可外评孰低是否为 AA-及以下：1=是 */
    private Integer inLowOuterRating;
}

package com.znty.rrs.entity.bo;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金当前池状态业务对象，对应 ip_pool_status_fund */
@Data
public class FundPoolStatusBo {
    /** 主键 ID */
    private Long id;
    /** 基金代码 */
    private String fundCode;
    /** 基金名称 */
    private String fundName;
    /** 基金简称 */
    private String fundShortName;
    /** 基金产品类型编码 */
    private String securityType;
    /** 基金评分 */
    private BigDecimal fundScore;
    /** 基金投资类型 */
    private String fundInvestmentType;
    /** 是否需要风管领导审批 */
    private Integer needRiskLeaderApproval;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 目标池名称 */
    private String targetPoolName;
    /** 投资池类型 */
    private String poolType;
    /** 入池时间 */
    private Date entryTime;
}

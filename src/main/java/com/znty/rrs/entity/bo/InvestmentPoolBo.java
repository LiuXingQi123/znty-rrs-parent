package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池业务对象，对应投资池主表，存储投资池的层级结构、流程配置及容量限制信息
 */
@Data
public class InvestmentPoolBo {

    /** 主键 ID */
    private Long id;

    /** 父级投资池 ID */
    private Long parentId;

    /** 投资池编码 */
    private String poolCode;

    /** 投资池名称 */
    private String poolName;

    /** 投资池类型 */
    private String poolType;

    /** 投资池层级 */
    private Integer poolLevel;

    /** 投资市场编码 JSON */
    private String marketCodes;

    /** 投资品种编码 JSON */
    private String varietyCodes;

    /** 恒生池名称 */
    private String hsPoolName;

    /** 标准调入流程 ID */
    private Long inFlowId;

    /** 标准调入流程 Key */
    private String inFlowKey;

    /** 标准调入流程名称 */
    private String inFlowName;

    /** 标准调出流程 ID */
    private Long outFlowId;

    /** 标准调出流程 Key */
    private String outFlowKey;

    /** 标准调出流程名称 */
    private String outFlowName;

    /** 简易调入流程 ID */
    private Long simpleInFlowId;

    /** 简易调入流程 Key */
    private String simpleInFlowKey;

    /** 简易调入流程名称 */
    private String simpleInFlowName;

    /** 简易调出流程 ID */
    private Long simpleOutFlowId;

    /** 简易调出流程 Key */
    private String simpleOutFlowKey;

    /** 简易调出流程名称 */
    private String simpleOutFlowName;

    /** 批量调入流程 ID */
    private Long batchInFlowId;

    /** 批量调入流程 Key */
    private String batchInFlowKey;

    /** 批量调入流程名称 */
    private String batchInFlowName;

    /** 批量调出流程 ID */
    private Long batchOutFlowId;

    /** 批量调出流程 Key */
    private String batchOutFlowKey;

    /** 批量调出流程名称 */
    private String batchOutFlowName;

    /** 调入研报限制 */
    private String inReportRestriction;

    /** 调出研报限制 */
    private String outReportRestriction;

    /** 最大上限数量 */
    private Long maxCapacity;

    /** 池锁定标志：0=未锁定 / 1=已锁定（锁定后不可调入/调出），空视为未锁定 */
    private Integer lockFlag;

    /** 调入冻结期天数：入池后N天内不可调出，空表示不限制 */
    private Integer frozenPeriodIn;

    /** 股票入池评级限制：允许的股票研究评级 code 列表（逗号分隔），空表示不限制 */
    private String gradeAstrict;

    /** 行业限制：证券行业须匹配此值（与 SecurityInfoBo.industryName 名称比对，老项目用编码前缀匹配当前用名称精确匹配），空表示不限制 */
    private String industryCode;

    /** 行业指数模式：!=0 时跳过行业校验，空视为 0 */
    private Integer industryExponent;

    /** 基金评分限制表达式（如 3<=#rate<=8），#rate 占位基金评分；调入校验基金评分须满足，空表示不限制 */
    private String fundRateLimit;

    /** 开放日校验开关：1=启用（调库日期须在 ip_pool_open_day 开放区间内），空/0=不限制 */
    private Integer openDayAdjust;

    /** 投资池外部排序 */
    private Integer outerSort;

    /** 投资池内部排序 */
    private Integer innerSort;

    /** 投资池描述 */
    private String description;

    /** 状态 */
    private String status;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

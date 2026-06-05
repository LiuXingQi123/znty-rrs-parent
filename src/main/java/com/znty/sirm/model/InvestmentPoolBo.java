package com.znty.sirm.model;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

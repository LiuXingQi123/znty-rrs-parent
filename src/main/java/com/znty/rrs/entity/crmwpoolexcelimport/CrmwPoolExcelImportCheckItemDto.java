package com.znty.rrs.entity.crmwpoolexcelimport;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CRMW 池 Excel 导入 — 调库校验结果项
 */
@Data
public class CrmwPoolExcelImportCheckItemDto {

    /** 来源导入明细 ID */
    private Long sourceItemId;
    /** 来源 Excel 行号 */
    private Integer sourceRowNo;
    /** 分组键（同组手工+联动+互斥） */
    private String adjustGroupKey;
    /** CRMW 代码 */
    private String crmwScode;
    /** CRMW 全称 */
    private String crmwName;
    /** CRMW 市场编码（逗号分隔） */
    private String crmwMarket;
    /** CRMW 证券类型 */
    private String crmwStype;
    /** 标的证券代码 */
    private String securityCode;
    /** 标的证券全称 */
    private String securityName;
    /** 标的证券简称 */
    private String securityShortName;
    /** 标的证券类型 */
    private String securityType;
    /** 标的证券类型名称 */
    private String securityTypeName;
    /** 标的证券市场编码（逗号分隔） */
    private String securityMarket;
    /** 触发主组合键（crmwScode|securityCode） */
    private String sourceSecurityCode;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 目标池名称 */
    private String targetPoolName;
    /** 目标池类型 */
    private String poolType;
    /** 调整模式：调入 / 调出 */
    private String adjustMode;
    /** 项标签：manual / linkage / mutex / related */
    private String itemTag;
    /** 调整类型中文 */
    private String adjustType;
    /** 是否可调整 */
    private boolean canAdjust;
    /** 失败原因列表 */
    private List<String> failReasons = new ArrayList<>();
    /** 调整说明（通过时） */
    private String adjustNote;
    /** 可选审批流程列表 */
    private List<FlowOptionDto> flowOptions = new ArrayList<>();
    /** 前端选中的流程 optionKey */
    private String selectedFlowKey;
    /** 选中流程 ID */
    private Long flowId;
    /** 选中流程 Key */
    private String flowKey;
    /** 选中流程类型 */
    private String flowType;
    /** 选中流程名称 */
    private String flowName;
    /** 是否直通（无需审批） */
    private boolean directFlow;

    /**
     * 流程候选项（对齐 CRMW 单笔 / 批量）
     */
    @Data
    public static class FlowOptionDto {

        /** 前端选中用唯一键 */
        private String optionKey;
        /** 流程类型 */
        private String flowType;
        /** 流程名称 */
        private String flowName;
        /** 流程 ID */
        private Long flowId;
        /** 流程 Key */
        private String flowKey;
        /** 是否为后端推荐流程 */
        private boolean recommended;
        /** 业务条件是否命中 */
        private boolean matched;
        /** 前端是否可选择 */
        private boolean selectable;
        /** 匹配原因 */
        private List<String> matchReasons = new ArrayList<>();
        /** 未匹配原因 */
        private List<String> unmatchReasons = new ArrayList<>();
    }
}

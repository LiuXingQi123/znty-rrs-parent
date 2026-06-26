package com.znty.sirm.entity.flow;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 流程候选项构建参数，聚合 buildFlowOption / buildPoolFlowOption 所需全部字段。
 *
 * <p>后续新增字段只需在此类中添加并设默认值，无需修改 buildXxxFlowOption 方法签名。
 */
@Data
public class FlowOptionParam {

    /** 流程类型（如 normalInbound / simpleInbound / whitelistInbound） */
    private String flowType;

    /** 流程显示名称 */
    private String flowName;

    /** 流程定义 ID */
    private Long flowId;

    /** 流程标识 Key */
    private String flowKey;

    /** 是否推荐流程（前端据此设定默认选中项） */
    private boolean recommended;

    /** 是否命中流程匹配条件 */
    private boolean matched;

    /** 是否允许用户选择此流程 */
    private boolean selectable;

    /** 命中的匹配原因 */
    private List<String> matchReasons;

    /** 未命中的原因 */
    private List<String> unmatchReasons;

    /** 获取流程匹配原因列表 */
    public List<String> getMatchReasons() {
        return matchReasons != null ? matchReasons : new ArrayList<>();
    }

    /** 获取流程不匹配原因列表 */
    public List<String> getUnmatchReasons() {
        return unmatchReasons != null ? unmatchReasons : new ArrayList<>();
    }
}

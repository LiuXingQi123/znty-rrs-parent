package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 流程画布节点 DTO，表示流程设计器中各类节点的类型、位置及配置（审批/自动/通知/条件）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanvasNodeDto {
    /** 节点唯一标识 */
    private String id;
    /** 节点类型：start/end/approval/auto/notify/condition */
    private String type;
    /** 节点名称 */
    private String label;
    /** 画布 X 坐标 */
    private Double x;
    /** 画布 Y 坐标 */
    private Double y;
    /** 形状 */
    private String shape;
    /** 副标题 */
    private String sub;
    // 审批节点
    /** 审批策略 */
    private String approvalStrategy;
    /** 审批人列表 */
    private List<String> approvalPersons;
    /** 审批备注 */
    private String approvalRemark;
    // 自动任务节点
    /** 自动任务列表 */
    private List<AutoTaskItemDto> autoTasks;
    /** 自动任务备注 */
    private String autoRemark;
    // 通知节点
    /** 通知目标 */
    private String notifyTarget;
    /** 通知渠道 */
    private List<String> notifyChannels;
    /** 通知人列表 */
    private List<String> notifyPersons;
    /** 通知模板 */
    private String notifyTpl;
    /** 通知备注 */
    private String notifyRemark;
    // 条件节点
    /** 条件备注 */
    private String conditionRemark;
}

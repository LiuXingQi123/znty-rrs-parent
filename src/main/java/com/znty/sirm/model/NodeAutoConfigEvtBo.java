package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 自动执行节点配置审计事件表，记录自动节点任务配置数据的每次变更历史
 */
@Data
public class NodeAutoConfigEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始配置记录 ID */
    private Long id;
    /** 关联的流程节点 ID */
    private Long nodeId;
    /** 任务执行序号（同一节点内排序） */
    private Integer taskSeq;
    /** 任务编码（对应系统预定义的自动任务） */
    private String taskCode;
    /** 自动节点备注说明 */
    private String autoRemark;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
    /** 操作人 ID */
    private String opterId;
    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date optTime;
    /** 操作类型（INSERT=新增/UPDATE=修改/DELETE=删除） */
    private String oprtType;
}

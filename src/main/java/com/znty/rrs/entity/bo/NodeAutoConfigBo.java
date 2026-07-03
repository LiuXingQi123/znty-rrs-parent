package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 自动执行节点任务配置表实体，存储流程中自动节点需依次执行的任务列表
 */
@Data
public class NodeAutoConfigBo {
    /** 主键 */
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
}

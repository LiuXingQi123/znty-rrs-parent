package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 自动执行节点任务配置
 */
@Data
public class NodeAutoConfigBo {
    /** 主键 */
    private Long id;
    /** 节点 ID */
    private Long nodeId;
    /** 任务序号 */
    private Integer taskSeq;
    /** 任务编码 */
    private String taskCode;
    /** 自动任务备注 */
    private String autoRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}

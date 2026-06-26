package com.znty.sirm.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 条件节点配置
 */
@Data
public class NodeConditionConfigBo {
    /** 主键 */
    private Long id;
    /** 节点 ID */
    private Long nodeId;
    /** 条件备注 */
    private String conditionRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}

package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 审批节点处理人明细表实体，按角色或人员维度存储审批节点处理人配置
 */
@Data
public class NodeApprovalHandlerBo {

    /** 主键 ID */
    private Long id;

    /** 审批节点配置 ID */
    private Long approvalConfigId;

    /** 处理人类型：role / user */
    private String handlerType;

    /** 处理人 ID */
    private Long handlerId;

    /** 处理人名称快照 */
    private String handlerName;

    /** 排序序号 */
    private Integer sortOrder;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

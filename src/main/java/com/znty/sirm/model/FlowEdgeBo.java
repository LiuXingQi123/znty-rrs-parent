package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线表实体，记录流程版本中各节点之间的有向连线及条件配置
 */
@Data
public class FlowEdgeBo {
    /** 主键 */
    private Long id;
    /** 所属流程版本 ID */
    private Long versionId;
    /** 所属流程定义 ID */
    private Long flowId;
    /** 业务连线 ID（前端画布生成的字符串 ID） */
    private String edgeId;
    /** 起始节点数据库代理键 ID */
    private Long fromNodeId;
    /** 目标节点数据库代理键 ID */
    private Long toNodeId;
    /** 连线标签（显示文本） */
    private String label;
    /** 条件逻辑：and=全部满足/or=任意满足 */
    private String condLogic;
    /** 连线备注 */
    private String remark;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

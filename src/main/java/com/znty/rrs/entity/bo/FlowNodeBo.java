package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程节点表实体，记录流程版本中各画布节点的类型、位置及配置信息
 */
@Data
public class FlowNodeBo {
    /** 主键（数据库代理键） */
    private Long id;
    /** 所属流程版本 ID */
    private Long versionId;
    /** 所属流程定义 ID */
    private Long flowId;
    /** 业务节点 ID（前端画布生成的字符串 ID） */
    private String nodeId;
    /** 节点类型：start=开始/end=结束/approval=审批/auto=自动/notify=通知/condition=条件 */
    private String nodeType;
    /** 节点显示名称 */
    private String label;
    /** 节点形状 */
    private String shape;
    /** 画布 X 坐标 */
    private Double posX;
    /** 画布 Y 坐标 */
    private Double posY;
    /** 节点副标题 */
    private String subLabel;
    /** 节点排序序号 */
    private Integer sortOrder;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

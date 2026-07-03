package com.znty.rrs.entity.flow;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 流程设计器请求对象，合并草稿保存和发布请求，携带画布节点、连线及布局信息
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignerReq {
    /** 流程 ID */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识 */
    private String flowKey;
    /** 发布说明（发布时使用） */
    private String publishNote;
    /** 画布节点列表 */
    private List<CanvasNodeDto> nodes;
    /** 画布连线列表 */
    private List<CanvasEdgeDto> edges;
    /** 画布平移 X */
    private Double panX;
    /** 画布平移 Y */
    private Double panY;
    /** 画布缩放 */
    private Double zoom;
}

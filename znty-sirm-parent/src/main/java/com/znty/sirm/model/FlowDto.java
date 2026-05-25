package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Date;

/**
 * 流程 DTO —— 合并 FlowListDto / FlowDetailDto / SaveResultDto
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowDto {
    // ===== 基本信息（列表 & 详情共用） =====
    /** 流程 ID */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识 */
    private String flowKey;
    /** 业务分类 */
    private String category;
    /** 描述 */
    private String description;
    /** 备注 */
    private String remark;
    /** 状态：draft/active/disabled */
    private String status;
    /** 当前版本号 */
    private Integer currentVer;
    /** 创建人 */
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;

    // ===== 保存/发布结果 =====
    /** 流程 ID（保存结果） */
    private Long flowId;
    /** 版本 ID */
    private Long versionId;
    /** 版本序号 */
    private Integer verNum;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 发布时间 */
    private Date publishedTime;

    // ===== 详情 —— 草稿/画布数据 =====
    /** 草稿信息 */
    private DraftInfo draft;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DraftInfo {
        /** 版本 ID */
        private Long versionId;
        /** 版本序号 */
        private Integer verNum;
        /** 画布节点 JSON */
        private Object nodes;
        /** 画布连线 JSON */
        private Object edges;
        /** 画布平移 X */
        private Double panX;
        /** 画布平移 Y */
        private Double panY;
        /** 画布缩放 */
        private Double zoom;
    }
}

package com.znty.rrs.entity.flow;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Date;

/**
 * 流程 DTO，合并列表、详情和保存结果，统一返回前端的流程数据传输对象
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowDto {
    // ===== 基本信息（列表 & 详情共用） =====
    /** 流程 ID */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识（flowKey） */
    private String flowKey;
    /** 业务分类 */
    private String category;
    /** 流程描述 */
    private String description;
    /** 备注 */
    private String remark;
    /** 状态：draft=草稿/active=已发布/disabled=已停用 */
    private String status;
    /** 当前版本号 */
    private Integer currentVer;
    /** 是否有已发布版本 */
    private Boolean hasPublishedVersion;
    /** 创建人 ID */
    private Long createdBy;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;

    // ===== 保存/发布结果 =====
    /** 流程 ID（保存结果返回） */
    private Long flowId;
    /** 版本 ID */
    private Long versionId;
    /** 版本序号 */
    private Integer verNum;
    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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

package com.znty.rrs.entity.flow;

import com.znty.rrs.common.PageRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程请求对象，合并列表查询、新建编辑和版本详情查询的入参，统一接收前端请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowReq extends PageRequest {
    // ===== 列表查询 =====
    /** 搜索关键字（名称/Key） */
    private String keyword;
    /** 状态筛选 */
    private String status;
    /** 业务分类筛选 */
    private String category;

    // ===== 新建/编辑 =====
    /** 流程 ID（编辑/版本详情） */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识（非必填） */
    private String flowKey;
    /** 描述 */
    private String description;
    /** 备注 */
    private String remark;

    // ===== 版本详情查询 =====
    /** 流程 ID */
    private Long flowId;
    /** 版本 ID */
    private Long versionId;
    /** 版本号筛选（版本历史查询） */
    private Integer verNum;

    /** 角色 ID（人员查询用） */
    private Long roleId;

    /** 人员搜索关键词 */
    private String userKeyword;

    /** 当前操作人用户 ID */
    private Long operatorId;
}

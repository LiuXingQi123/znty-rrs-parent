package com.znty.rrs.entity.mymatters;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 我的事宜分页查询请求对象。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MyMattersReq extends PageRequest {

    /** 流程 ID 列表，多选筛选 */
    private List<Long> flowIds;

    /** 证券编码关键词 */
    private String securityCode;

    /** 证券名称关键词 */
    private String securityShortName;

    /** 开始日期起，格式 yyyy-MM-dd */
    private String startDateStart;

    /** 开始日期止，格式 yyyy-MM-dd */
    private String startDateEnd;

    /** 流程描述关键词 */
    private String processDescription;

    /** 审核状态 */
    private String auditStatus;

    /** 步骤状态：pending=待处理 / completed=已完成 */
    private String stepStatus;

    /** 发起人姓名关键词 */
    private String initiatorName;

    /** 当前用户 ID，1 视为管理员 */
    private String currentUserId;
}

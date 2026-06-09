package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 待办处理分页查询请求对象。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TodoProcessReq extends PageRequest {

    /** 流程 ID 列表，多选筛选 */
    private List<Long> flowIds;

    /** 开始日期起，格式 yyyy-MM-dd */
    private String startDateStart;

    /** 开始日期止，格式 yyyy-MM-dd */
    private String startDateEnd;

    /** 流程描述关键词 */
    private String processDescription;

    /** 发起人姓名关键词 */
    private String initiatorName;

    /** 当前用户 ID，1001 视为管理员 */
    private String currentUserId;
}

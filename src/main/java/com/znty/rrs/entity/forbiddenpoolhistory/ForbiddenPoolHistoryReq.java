package com.znty.rrs.entity.forbiddenpoolhistory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 禁投池历史查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenPoolHistoryReq extends PageRequest {

    /** 当前用户 ID */
    private String currentUserId;

    /** 服务端解析的可查看投资池 ID */
    @JsonIgnore
    private List<Long> viewablePoolIds;

    /** 主体代码（模糊搜索） */
    private String companyCode;

    /** 主体名称（模糊搜索） */
    private String companyName;

    /** 证券代码（模糊搜索） */
    private String securityCode;

    /** 证券名称（模糊搜索） */
    private String securityShortName;

    /** 提交日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 提交日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 调整状态 */
    private String auditStatus;
}

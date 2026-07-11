package com.znty.rrs.entity.companypool;

import com.znty.rrs.common.PageRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 主体池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPoolQueryReq extends PageRequest {

    /** 当前用户 ID */
    private String currentUserId;

    /** 服务端解析的可查看投资池 ID */
    @JsonIgnore
    private List<Long> viewablePoolIds;

    /** 投资池 ID 列表（树多选） */
    private List<Long> poolIds;

    /** 主体代码（模糊搜索） */
    private String companyCode;

    /** 入池时间起 */
    private String entryTimeStart;

    /** 入池时间止 */
    private String entryTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;
}

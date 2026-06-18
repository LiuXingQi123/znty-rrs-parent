package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 主体池调整历史查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPoolAdjustHistoryReq extends PageRequest {

    /** 投资池 ID 列表（树多选） */
    private List<Long> poolIds;

    /** 主体代码（模糊搜索） */
    private String companyCode;

    /** 主体名称（模糊搜索） */
    private String companyName;

    /** 调整日期起（yyyy-MM-dd） */
    private String adjustTimeStart;

    /** 调整日期止（yyyy-MM-dd） */
    private String adjustTimeEnd;

    /** 调整人（模糊搜索） */
    private String adjusterName;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 调整状态 */
    private String auditStatus;
}

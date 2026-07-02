package com.znty.sirm.entity.companypool;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 主体池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPoolQueryReq extends PageRequest {

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

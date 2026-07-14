package com.znty.rrs.entity.poolopenday;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.znty.rrs.common.PageRequest;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投资池开放日维护请求对象，承载查询条件与增删改参数
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PoolOpenDayReq extends PageRequest {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID */
    private Long poolId;

    /** 开放区间起始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDate;

    /** 开放区间结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    /** 描述 */
    private String description;

    /** 查询：开放起始日下限（含） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDateFrom;

    /** 查询：开放结束日上限（含） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDateTo;
}

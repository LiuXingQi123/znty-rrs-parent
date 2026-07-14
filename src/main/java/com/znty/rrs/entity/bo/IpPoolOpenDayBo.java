package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池开放日业务对象，对应表 ip_pool_open_day
 */
@Data
public class IpPoolOpenDayBo {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID，关联 ip_investment_pool.id */
    private Long poolId;

    /** 开放区间起始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDate;

    /** 开放区间结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    /** 描述 */
    private String description;

    /** 逻辑删除标志：0=正常 / 1=已删除 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

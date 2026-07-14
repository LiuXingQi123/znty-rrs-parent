package com.znty.rrs.entity.poolopenday;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池开放日维护返回对象
 */
@Data
public class PoolOpenDayDto {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID */
    private Long poolId;

    /** 投资池名称（节点名） */
    private String poolName;

    /** 投资池全称（路径，如 信用债大库/一级库） */
    private String poolFullName;

    /**
     * 开放日校验开关：1=启用 / 空或0=不限制
     * <p>仅展示用，配置来自投资池主表 open_day_adjust
     */
    private Integer openDayAdjust;

    /** 开放区间起始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date beginDate;

    /** 开放区间结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    /** 描述 */
    private String description;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

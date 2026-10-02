package com.znty.rrs.entity.fundpooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/** 基金当前所在池返回对象 */
@Data
public class FundPoolStatusDto {
    /** 当前池状态 ID */
    private Long id;
    /** 投资池 ID */
    private Long targetPoolId;
    /** 投资池路径名称 */
    private String poolName;
    /** 投资池类型 */
    private String poolType;
    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;
}

package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 债券池调库请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BondPoolAdjustReq extends PageRequest {
    /** 债券代码（模糊搜索） */
    private String bondCode;
    /** 债券简称（模糊搜索） */
    private String bondShortName;
    /** 发行人（模糊搜索） */
    private String issuer;
    /** 债券 ID（查看详情用） */
    private Long bondId;
    /** 调库方向：in=可调入库 / out=可调出库 */
    private String adjustDirection;
}

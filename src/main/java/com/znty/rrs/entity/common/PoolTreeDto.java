package com.znty.rrs.entity.common;

import lombok.Data;

/**
 * 投资池树节点返回对象
 */
@Data
public class PoolTreeDto {

    /** 投资池 ID */
    private Long id;

    /** 父级投资池 ID */
    private Long parentId;

    /** 投资池节点名称 */
    private String poolName;

    /** 投资池全路径名称 */
    private String poolFullName;
}

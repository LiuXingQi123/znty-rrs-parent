package com.znty.sirm.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 投资池树节点返回对象（调库页面底部可调库/可调出库树展示）
 */
@Data
public class PoolTreeNodeDto {
    /** 投资池 ID */
    private Long id;
    /** 父级 ID */
    private Long parentId;
    /** 投资池名称 */
    private String poolName;
    /** 投资池编码 */
    private String poolCode;
    /** 投资池类型 */
    private String poolType;
    /** 投资池层级 */
    private Integer poolLevel;
    /** 上限数量 */
    private Long maxCapacity;
    /** 现有数量（占位，当前无 bond-pool 映射表） */
    private Integer currentCount;
    /** 附件报告 */
    private String attachmentReport;
    /** 其他材料 */
    private String otherMaterials;
    /** 子节点 */
    private List<PoolTreeNodeDto> children = new ArrayList<>();
}

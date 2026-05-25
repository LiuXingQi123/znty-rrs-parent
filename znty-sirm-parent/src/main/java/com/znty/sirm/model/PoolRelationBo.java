package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池关系业务对象
 */
@Data
public class PoolRelationBo {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID */
    private Long poolId;

    /** 关系类型 */
    private String relationType;

    /** 关联投资池 ID */
    private Long relationPoolId;

    /** 关联投资池名称 */
    private String relationPoolName;

    /** 排序序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

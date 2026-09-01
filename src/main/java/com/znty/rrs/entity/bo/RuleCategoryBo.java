package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 规则分类实体，对应表 rule_category。
 * <p>用于对规则按 checkAdjust 调用点分组（调入通用、调出通用、分级矩阵等）。</p>
 */
@Data
public class RuleCategoryBo {
    /** 主键 ID */
    private Long id;
    /** 分类编码（唯一标识，如 adjust_in、grade_matrix） */
    private String categoryCode;
    /** 分类名称 */
    private String categoryName;
    /** 排序号（越小越靠前） */
    private Integer sortNo;
    /** 启用状态：1-启用，0-停用 */
    private Integer enabled;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

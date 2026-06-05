package com.znty.sirm.model;

import lombok.Data;

/**
 * 规则分类字典 DTO，用于规则管理页面下拉列表的分类编码与名称映射
 */
@Data
public class CategoryDto {

    /** 分类编码 */
    private String code;

    /** 分类名称 */
    private String name;
}

package com.znty.sirm.model;

import lombok.Data;

/**
 * 规则分类字典返回对象
 */
@Data
public class CategoryDto {

    /** 分类编码 */
    private String code;

    /** 分类名称 */
    private String name;
}

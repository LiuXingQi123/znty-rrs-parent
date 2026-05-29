package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 预设选项集返回对象
 */
@Data
public class PresetSetDto {

    /** 选项集 ID */
    private Long id;

    /** 选项集名称 */
    private String name;

    /** 选项值列表 */
    private List<String> options;
}

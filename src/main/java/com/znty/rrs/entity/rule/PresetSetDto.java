package com.znty.rrs.entity.rule;

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

    /** 选项列表（value=脚本取值，label=中文名称） */
    private List<PresetOptionDto> options;
}

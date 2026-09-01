package com.znty.rrs.entity.rule;

import lombok.Data;

/**
 * 预设或参数选项：脚本取值用 value，页面展示用 label。
 */
@Data
public class PresetOptionDto {

    /** 选项值（写入脚本变量） */
    private String value;

    /** 选项中文名称 */
    private String label;
}

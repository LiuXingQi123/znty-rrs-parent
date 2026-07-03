package com.znty.rrs.entity.bo;

import lombok.Data;

/**
 * 预设选项明细实体，对应表 rule_preset_option_item。
 * <p>存储预设选项集的各个选项值及排序。</p>
 */
@Data
public class RulePresetOptionItemBo {
    /** 主键 ID */
    private Long id;
    /** 所属选项集 ID */
    private Long setId;
    /** 选项值 */
    private String optionValue;
    /** 选项显示名称 */
    private String optionLabel;
    /** 排序号 */
    private Integer sortNo;
}

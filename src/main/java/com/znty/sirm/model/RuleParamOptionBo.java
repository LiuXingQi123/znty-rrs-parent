package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 规则参数选项实体，对应表 rule_param_option。
 * <p>为 select/multiselect 类型的参数提供可选值列表。</p>
 */
@Data
public class RuleParamOptionBo {
    /** 主键 ID */
    private Long id;
    /** 所属参数 ID */
    private Long paramId;
    /** 选项值 */
    private String optionValue;
    /** 选项显示名称 */
    private String optionLabel;
    /** 排序号 */
    private Integer sortNo;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

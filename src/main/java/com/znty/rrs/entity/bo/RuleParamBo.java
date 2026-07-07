package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 规则参数实体，对应表 rule_param。
 * <p>定义规则执行时所需的输入参数，包括名称、标签、类型和是否必填。</p>
 */
@Data
public class RuleParamBo {
    /** 主键 ID */
    private Long id;
    /** 所属规则 ID */
    private Long ruleId;
    /** 参数名（脚本变量名，如 orderAmount） */
    private String paramName;
    /** 参数显示名称（如"订单金额"） */
    private String paramLabel;
    /** 参数类型：string-字符串，number-数值，select-单选，multiselect-多选 */
    private String paramType;
    /** 是否必填：1-必填，0-非必填 */
    private Integer required;
    /** 排序号 */
    private Integer sortNo;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

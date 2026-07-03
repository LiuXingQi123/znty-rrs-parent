package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 参数预设选项集实体，对应表 rule_preset_option_set。
 * <p>定义一组可复用的预设选项，供多个规则参数的选项列表引用。</p>
 */
@Data
public class RulePresetOptionSetBo {
    /** 主键 ID */
    private Long id;
    /** 选项集名称（如"主体评级"、"证券类型"） */
    private String setName;
    /** 排序号 */
    private Integer sortNo;
    /** 启用状态：1-启用，0-停用 */
    private Integer enabled;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线条件规则表，存储每条连线上配置的条件规则明细
 */
@Data
public class EdgeCondRuleBo {
    /** 主键 */
    private Long id;
    /** 所属连线 ID */
    private Long edgeId;
    /** 规则序号（同一连线内排序） */
    private Integer seq;
    /** 条件字段编码 */
    private String fieldCode;
    /** 操作符（eq=等于/ne=不等于/gt=大于/lt=小于/in=包含） */
    private String operator;
    /** 条件值 */
    private String fieldVal;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

package com.znty.sirm.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线条件规则审计事件表，记录条件规则数据的变更历史
 */
@Data
public class EdgeCondRuleEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始记录 ID */
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
    /** 操作人 ID */
    private String opterId;
    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date optTime;
    /** 操作类型（INSERT=新增/UPDATE=修改/DELETE=删除） */
    private String oprtType;
}

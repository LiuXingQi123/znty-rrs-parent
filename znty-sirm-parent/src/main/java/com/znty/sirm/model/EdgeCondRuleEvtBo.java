package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 连线条件规则事件表
 */
@Data
public class EdgeCondRuleEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 连线 ID */
    private Long edgeId;
    /** 规则序号 */
    private Integer seq;
    /** 条件字段编码 */
    private String fieldCode;
    /** 操作符 */
    private String operator;
    /** 条件值 */
    private String fieldVal;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
    /** 操作人 ID */
    private String opterId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 操作时间 */
    private Date optTime;
    /** 操作类型 */
    private String oprtType;
}

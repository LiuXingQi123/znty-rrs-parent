package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 规则定义实体，对应表 rule_definition。
 * <p>存储规则的核心信息，包括 QLExpress 脚本、分类和状态。</p>
 */
@Data
public class RuleDefinitionBo {
    /** 主键 ID */
    private Long id;
    /** 规则名称 */
    private String ruleName;
    /** 规则描述 */
    private String description;
    /** 所属分类编码 */
    private String categoryCode;
    /** QLExpress 规则脚本 */
    private String script;
    /** 规则状态：active-启用，disabled-禁用 */
    private String status;
    /** 删除标记：0-未删除，1-已删除（软删除） */
    private Integer deletedFlag;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}

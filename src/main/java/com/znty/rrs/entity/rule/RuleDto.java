package com.znty.rrs.entity.rule;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规则 DTO，包含规则基本信息、参数列表和脚本内容。
 */
@Data
public class RuleDto {
    /** 规则 ID */
    private Long id;
    /** 规则名称 */
    private String name;
    /** 规则描述 */
    private String description;
    /** 所属分类编码 */
    private String category;
    /** 规则参数列表，每项含 id/label/name/type/options 键 */
    private List<Map<String, Object>> params = new ArrayList<>();
    /** QLExpress 规则脚本 */
    private String script;
    /** 规则状态：active-启用，disabled-禁用 */
    private String status;
    /** 创建时间（yyyy-MM-dd HH:mm:ss 格式字符串） */
    private String createdAt;
}

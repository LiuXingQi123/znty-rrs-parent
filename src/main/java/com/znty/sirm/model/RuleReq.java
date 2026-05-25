package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则模块统一请求对象，合并了列表查询、新增/编辑、状态更新和规则执行的字段。
 * <p>各接口按需使用其中的字段，未涉及的字段保持 null。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RuleReq extends PageRequest {
    /** 规则 ID（编辑/状态更新/执行时使用） */
    private Long id;
    /** 规则名称（新增/编辑时使用） */
    private String name;
    /** 规则描述（新增/编辑时使用） */
    private String description;
    /** 所属分类编码（新增/编辑时使用） */
    private String category;
    /** 规则参数定义列表（新增/编辑时使用），每项含 id/label/name/type/options 键 */
    private List<Map<String, Object>> paramList = new ArrayList<>();
    /** QLExpress 规则脚本（新增/编辑时使用） */
    private String script;
    /** 搜索关键字（列表查询时使用，匹配规则名称/描述/参数） */
    private String keyword;
    /** 状态：列表查询时为筛选条件，状态更新时为目标值（active / disabled） */
    private String status;
    /** 运行时参数键值对（规则执行时使用） */
    private Map<String, String> runParams = new LinkedHashMap<>();
}

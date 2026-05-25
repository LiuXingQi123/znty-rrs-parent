package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用例模块统一请求对象，合并了列表查询、新增/编辑和重命名的字段。
 * <p>各接口按需使用其中的字段，未涉及的字段保持 null。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TestCaseReq extends PageRequest {
    /** 用例 ID（编辑/重命名/删除/执行时使用） */
    private Long id;
    /** 用例名称（新增/编辑/重命名时使用） */
    private String name;
    /** 关联规则 ID（新增/编辑时使用） */
    private Long ruleId;
    /** 用例参数键值对（新增/编辑时使用） */
    private Map<String, String> params = new LinkedHashMap<>();
}

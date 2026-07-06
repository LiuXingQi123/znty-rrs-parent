package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本工具只读检查结果。
 */
@Data
public class ScriptInspectionDto {

    /** 整体状态：success=正常 / warning=警告 / failed=失败 */
    private String status;

    /** 检查对象总数 */
    private Integer totalCount;

    /** 正常项数量 */
    private Integer successCount;

    /** 警告项数量 */
    private Integer warningCount;

    /** 失败项数量 */
    private Integer failedCount;

    /** 问题数据总数 */
    private Long issueCount;

    /** 检查说明 */
    private String summary;

    /** 检查项列表 */
    private List<ScriptInspectionItemDto> items;
}

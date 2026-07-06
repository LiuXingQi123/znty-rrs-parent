package com.znty.rrs.entity.scripttool;

import lombok.Data;

/**
 * 脚本工具只读检查项。
 */
@Data
public class ScriptInspectionItemDto {

    /** 检查项编码 */
    private String itemCode;

    /** 检查分类 */
    private String category;

    /** 检查对象 */
    private String objectName;

    /** 检查项名称 */
    private String itemName;

    /** 检查状态：success=正常 / warning=警告 / failed=失败 */
    private String status;

    /** 当前值 */
    private String currentValue;

    /** 期望值 */
    private String expectedValue;

    /** 问题数据数量 */
    private Long issueCount;

    /** 检查说明 */
    private String message;

    /** 处理建议 */
    private String suggestion;
}

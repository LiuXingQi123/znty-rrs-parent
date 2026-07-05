package com.znty.rrs.entity.scripttool;

import lombok.Data;

/**
 * 脚本工具环境健康检查项。
 */
@Data
public class ScriptHealthItemDto {

    /** 检查项编码 */
    private String itemCode;

    /** 检查项名称 */
    private String itemName;

    /** 检查状态：success=正常 / warning=警告 / failed=失败 */
    private String status;

    /** 当前值 */
    private String currentValue;

    /** 期望值 */
    private String expectedValue;

    /** 检查说明 */
    private String message;
}

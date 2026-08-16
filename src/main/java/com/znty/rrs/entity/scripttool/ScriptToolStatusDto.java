package com.znty.rrs.entity.scripttool;

import lombok.Data;

/**
 * 脚本工具开关状态。
 */
@Data
public class ScriptToolStatusDto {

    /** 是否允许执行写操作（建表/灌数/清空/重置/场景生成） */
    private Boolean enabled;
}

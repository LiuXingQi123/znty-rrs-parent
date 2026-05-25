package com.znty.sirm.model;

import lombok.Data;

/**
 * 自动任务项
 */
@Data
public class AutoTaskItemDto {
    /** 任务序号 */
    private Integer id;
    /** 任务编码 */
    private String task;
}

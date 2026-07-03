package com.znty.rrs.entity.flow;

import lombok.Data;

/**
 * 自动任务项 DTO，表示流程自动节点中单个任务的序号和编码信息
 */
@Data
public class AutoTaskItemDto {
    /** 任务序号 */
    private Integer id;
    /** 任务编码 */
    private String task;
}

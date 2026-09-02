package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池自动调入调出定时任务绑定业务对象
 */
@Data
public class PoolAutoRuleBo {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID */
    private Long poolId;

    /** 规则类型 */
    private String ruleType;

    /** 关联定时任务 ID（sys_scheduled_task.id） */
    private Long ruleId;

    /** 定时任务编码（sys_scheduled_task.task_code） */
    private String taskCode;

    /** 定时任务名称快照 */
    private String ruleDesc;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}

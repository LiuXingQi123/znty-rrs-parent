package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.bo.SysScheduledTaskRunLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时任务配置与执行历史数据访问。
 */
@Mapper
public interface ScheduledTaskMapper {

    /** 查询全部有效任务配置 */
    List<SysScheduledTaskBo> queryTaskList();

    /** 按编码查询任务配置 */
    SysScheduledTaskBo queryTaskByCode(@Param("taskCode") String taskCode);

    /** 新增任务配置 */
    int addTask(SysScheduledTaskBo bo);

    /** 更新任务调度配置（cron / 启停 / 扩展参数） */
    int editTaskConfig(SysScheduledTaskBo bo);

    /** 更新最近执行摘要 */
    int editTaskLastRun(SysScheduledTaskBo bo);

    /** 写配置变更审计 */
    int addTaskEvent(@Param("bo") SysScheduledTaskBo bo,
                     @Param("opterId") String opterId,
                     @Param("oprtType") String oprtType);

    /** 新增执行历史 */
    int addRunLog(SysScheduledTaskRunLogBo bo);

    /** 分页查询执行历史（PageHelper 外层分页） */
    List<SysScheduledTaskRunLogBo> queryRunLogList(@Param("taskCode") String taskCode);
}

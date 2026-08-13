package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.bo.SysScheduledTaskRunLogBo;
import com.znty.rrs.entity.schedule.ScheduledTaskReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时任务配置与执行历史数据访问
 */
@Mapper
public interface ScheduledTaskMapper {

    /**
     * 查询全部未删除的定时任务配置列表，按主键升序
     */
    List<SysScheduledTaskBo> queryTaskList();

    /**
     * 分页查询未删除的定时任务配置（关键字、调度启停）
     */
    List<SysScheduledTaskBo> queryTaskPage(ScheduledTaskReq req);

    /**
     * 按任务编码查询单条未删除的定时任务配置
     */
    SysScheduledTaskBo queryTaskByCode(@Param("taskCode") String taskCode);

    /**
     * 新增定时任务配置（编码、名称、说明、cron、启停、扩展参数）
     */
    int addTask(SysScheduledTaskBo bo);

    /**
     * 按任务编码更新配置（名称、说明、cron、启停、扩展参数）
     */
    int editTask(SysScheduledTaskBo bo);

    /**
     * 按任务编码逻辑删除配置，并关闭调度开关
     */
    int deleteTaskSoft(@Param("taskCode") String taskCode);

    /**
     * 更新主表最近一次执行摘要（状态、说明、时间、影响条数、耗时、触发方式）
     */
    int editTaskLastRun(SysScheduledTaskBo bo);

    /**
     * 写入定时任务配置变更审计事件（快照 + 操作人 + 操作类型）
     */
    int addTaskEvent(@Param("bo") SysScheduledTaskBo bo,
                     @Param("opterId") String opterId,
                     @Param("oprtType") String oprtType);

    /**
     * 新增一条定时任务执行历史记录
     */
    int addRunLog(SysScheduledTaskRunLogBo bo);

    /**
     * 查询执行历史列表（可按任务编码筛选），由 PageHelper 做外层分页
     */
    List<SysScheduledTaskRunLogBo> queryRunLogList(@Param("taskCode") String taskCode);
}

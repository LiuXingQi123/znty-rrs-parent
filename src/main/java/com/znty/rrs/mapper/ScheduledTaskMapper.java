package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.bo.SysScheduledTaskRunLogBo;
import com.znty.rrs.entity.schedule.ScheduledTaskReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 定时任务配置与执行历史数据访问
 */
@Mapper
public interface ScheduledTaskMapper {

    /**
     * 查询全部未删除的定时任务配置列表，按主键升序
     *
     * @return 符合条件的定时任务配置列表
     */
    List<SysScheduledTaskBo> queryTaskList();

    /**
     * 分页查询未删除的定时任务配置（关键字、调度启停）
     *
     * @param req 定时任务配置查询条件
     * @return 符合条件的定时任务配置列表
     */
    List<SysScheduledTaskBo> queryTaskPage(ScheduledTaskReq req);

    /**
     * 按主键查询单条未删除的定时任务配置
     *
     * @param id 定时任务 ID
     * @return 定时任务配置，未找到时为 null
     */
    SysScheduledTaskBo queryTaskById(@Param("id") Long id);

    /**
     * 按任务编码查询单条未删除的定时任务配置
     *
     * @param taskCode 定时任务代码
     * @return 定时任务配置，未找到时为 null
     */
    SysScheduledTaskBo queryTaskByCode(@Param("taskCode") String taskCode);

    /**
     * 新增定时任务配置（编码、名称、说明、cron、启停、扩展参数）
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的定时任务配置
     * @return 受影响的行数
     */
    int addTask(SysScheduledTaskBo bo);

    /**
     * 按任务编码更新配置（名称、说明、cron、启停、扩展参数）
     *
     * @param bo 任务代码及要修改的名称、说明、Cron、启用状态和参数
     * @return 受影响的行数
     */
    int editTask(SysScheduledTaskBo bo);

    /**
     * 按任务编码逻辑删除配置，并关闭调度开关
     *
     * @param taskCode 定时任务代码
     * @return 受影响的行数
     */
    int deleteTaskSoft(@Param("taskCode") String taskCode);

    /**
     * 更新主表最近一次执行摘要（状态、说明、时间、影响条数、耗时、触发方式）
     *
     * @param bo 任务代码及最近一次执行的状态、时间和结果
     * @return 受影响的行数
     */
    int editTaskLastRun(SysScheduledTaskBo bo);

    /**
     * 写入定时任务配置变更审计事件（快照 + 操作人 + 操作类型）
     *
     * @param bo 待写入的定时任务配置
     * @param opterId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addTaskEvent(@Param("bo") SysScheduledTaskBo bo,
                     @Param("opterId") String opterId,
                     @Param("oprtType") String oprtType);

    /**
     * 新增一条定时任务执行历史记录
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的定时任务运行日志
     * @return 受影响的行数
     */
    int addRunLog(SysScheduledTaskRunLogBo bo);

    /**
     * 分页查询未删除的任务执行记录
     *
     * @param taskCode 定时任务代码，为空时不按任务筛选
     * @return 符合条件的定时任务运行日志列表
     */
    List<SysScheduledTaskRunLogBo> queryRunLogPage(@Param("taskCode") String taskCode);

    /**
     * 查询指定任务最近一次成功执行的开始时间
     *
     * @param taskCode 定时任务代码
     * @return 最近一次成功执行的开始时间，无成功记录时为 null
     */
    Date queryLastSuccessStartTime(@Param("taskCode") String taskCode);
}

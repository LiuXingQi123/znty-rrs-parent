package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.ScheduleRunStatus;
import com.znty.rrs.common.enums.ScheduleTriggerType;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.bo.SysScheduledTaskRunLogBo;
import com.znty.rrs.entity.schedule.ScheduledTaskInfoDto;
import com.znty.rrs.entity.schedule.ScheduledTaskReq;
import com.znty.rrs.entity.schedule.ScheduledTaskRunLogDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.DynamicTaskScheduler;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务注册、配置持久化、动态调度与手动触发编排。
 *
 * <p>代码侧 {@link RrsScheduledTask} 提供默认元信息与业务执行体；
 * 库表 {@code sys_scheduled_task} 持久化 cron / 启停 / 扩展参数；
 * 页面修改配置后即时经 {@link DynamicTaskScheduler} 重挂载。
 */
@Slf4j
@Service
public class ScheduledTaskService {

    /** 系统操作人 ID（定时触发） */
    private static final String SYSTEM_OPERATOR_ID = "0";
    /** 系统操作人名称（定时触发） */
    private static final String SYSTEM_OPERATOR_NAME = "系统";
    /** 配置审计操作类型：修改 */
    private static final String OPRT_TYPE_EDIT = "修改";

    /** 按 taskCode 索引的任务实现 */
    private final Map<String, RrsScheduledTask> taskMap;
    /** 同任务串行执行锁 */
    private final Map<String, Object> runLocks = new ConcurrentHashMap<>();

    /** 定时任务配置与历史 Mapper */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 动态 cron 调度器 */
    @Resource
    private DynamicTaskScheduler dynamicTaskScheduler;

    /**
     * 注入全部 {@link RrsScheduledTask} 实现并建立编码索引。
     *
     * @param taskList Spring 收集的任务实现列表
     */
    public ScheduledTaskService(List<RrsScheduledTask> taskList) {
        Map<String, RrsScheduledTask> map = new LinkedHashMap<>();
        if (taskList != null) {
            for (RrsScheduledTask task : taskList) {
                if (task == null || !StringUtils.hasText(task.getTaskCode())) {
                    continue;
                }
                String code = task.getTaskCode();
                if (map.containsKey(code)) {
                    throw new IllegalStateException("定时任务编码重复: " + code);
                }
                map.put(code, task);
            }
        }
        this.taskMap = Collections.unmodifiableMap(map);
        log.info("定时任务代码注册完成，共 {} 个: {}", this.taskMap.size(), this.taskMap.keySet());
    }

    /**
     * 启动后：按代码实现种子补全库表配置，并挂载已启用任务。
     */
    @PostConstruct
    public void initAfterStartup() {
        try {
            // 补全代码已注册但库中缺失的任务配置
            syncCodeTasksToDb();
            // 按库表配置挂载启用中的调度
            reloadAllSchedules();
        } catch (Exception e) {
            log.warn("定时任务启动同步失败（表可能尚未初始化，可在脚本工具执行 rrs_scheduled_task_schema 后重启）: {}",
                    e.getMessage());
        }
    }

    /**
     * 查询任务清单（代码注册 ∪ 库表配置）。
     *
     * @return 任务信息列表
     */
    public List<ScheduledTaskInfoDto> queryTaskList() {
        // 加载库表配置索引
        Map<String, SysScheduledTaskBo> dbMap = loadDbTaskMap();
        LinkedHashMap<String, ScheduledTaskInfoDto> result = new LinkedHashMap<>();
        // 先按代码注册顺序输出
        for (RrsScheduledTask task : taskMap.values()) {
            // 合并代码元信息与库表配置
            result.put(task.getTaskCode(), toInfoDto(task, dbMap.get(task.getTaskCode())));
        }
        // 库中有但代码未注册的配置也展示
        for (SysScheduledTaskBo bo : dbMap.values()) {
            if (!result.containsKey(bo.getTaskCode())) {
                result.put(bo.getTaskCode(), toInfoDto(null, bo));
            }
        }
        return new ArrayList<>(result.values());
    }

    /**
     * 按编码查询任务。
     *
     * @param taskCode 任务编码
     * @return 任务信息
     */
    public ScheduledTaskInfoDto queryTask(String taskCode) {
        // 规范化编码
        String code = trimCode(taskCode);
        RrsScheduledTask task = taskMap.get(code);
        SysScheduledTaskBo bo = scheduledTaskMapper.queryTaskByCode(code);
        if (task == null && bo == null) {
            throw new BizException("未找到定时任务: " + taskCode);
        }
        // 组装展示 DTO
        return toInfoDto(task, bo);
    }

    /**
     * 保存任务配置（cron / 启停 / 扩展参数）并重挂载调度。
     *
     * @param req 配置请求
     * @return 更新后的任务信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto editTaskConfig(ScheduledTaskReq req) {
        if (req == null || !StringUtils.hasText(req.getTaskCode())) {
            throw new BizException("任务编码不能为空");
        }
        String taskCode = req.getTaskCode().trim();
        RrsScheduledTask codeTask = taskMap.get(taskCode);
        SysScheduledTaskBo existing = scheduledTaskMapper.queryTaskByCode(taskCode);
        if (existing == null && codeTask == null) {
            throw new BizException("未找到定时任务: " + taskCode);
        }
        // 解析并校验 cron
        String cron = req.getCronExpression() != null
                ? req.getCronExpression().trim()
                : (existing != null ? existing.getCronExpression() : codeTask.getDefaultCronExpression());
        if (!StringUtils.hasText(cron) || !dynamicTaskScheduler.isValidCron(cron)) {
            throw new BizException("cron 表达式非法，请使用 Spring 6 位格式，如 0 0 2 * * ?");
        }
        boolean enabled = req.getScheduleEnabled() != null
                ? req.getScheduleEnabled()
                : (existing != null && Integer.valueOf(1).equals(existing.getScheduleEnabled()));
        String paramJson = req.getParamJson() != null
                ? req.getParamJson().trim()
                : (existing != null ? existing.getParamJson()
                : (codeTask != null ? codeTask.getDefaultParamJson() : null));
        // 名称/描述允许页面编辑，未传则保留库表或代码默认值
        String taskName = StringUtils.hasText(req.getTaskName())
                ? req.getTaskName().trim()
                : (existing != null && StringUtils.hasText(existing.getTaskName())
                ? existing.getTaskName()
                : (codeTask != null ? codeTask.getTaskName() : taskCode));
        if (!StringUtils.hasText(taskName)) {
            throw new BizException("任务名称不能为空");
        }
        if (taskName.length() > 128) {
            throw new BizException("任务名称不能超过 128 字");
        }
        String description = req.getDescription() != null
                ? req.getDescription().trim()
                : (existing != null ? existing.getDescription()
                : (codeTask != null ? codeTask.getDescription() : null));
        if (description != null && description.length() > 500) {
            throw new BizException("任务说明不能超过 500 字");
        }

        SysScheduledTaskBo save = new SysScheduledTaskBo();
        save.setTaskCode(taskCode);
        save.setTaskName(taskName);
        save.setDescription(description);
        save.setCronExpression(cron);
        save.setScheduleEnabled(enabled ? 1 : 0);
        save.setParamJson(paramJson);

        if (existing == null) {
            // 新增库表配置
            scheduledTaskMapper.addTask(save);
        } else {
            // 更新已有配置
            scheduledTaskMapper.editTaskConfig(save);
            save.setId(existing.getId());
        }
        // 用本次保存值即时重挂载
        applySchedule(taskCode, save);
        // 回读最新配置
        SysScheduledTaskBo latest = scheduledTaskMapper.queryTaskByCode(taskCode);
        if (latest == null) {
            latest = save;
        }
        // 写配置变更审计
        scheduledTaskMapper.addTaskEvent(latest,
                StringUtils.hasText(req.getOperatorId()) ? req.getOperatorId() : SYSTEM_OPERATOR_ID,
                OPRT_TYPE_EDIT);
        // 组装返回 DTO
        return toInfoDto(codeTask, latest);
    }

    /**
     * 手动执行单个任务。
     *
     * @param req 执行请求（含操作人）
     * @return 执行结果
     */
    public ScheduledTaskResult executeTask(ScheduledTaskReq req) {
        if (req == null || !StringUtils.hasText(req.getTaskCode())) {
            throw new BizException("任务编码不能为空");
        }
        String operatorId = StringUtils.hasText(req.getOperatorId()) ? req.getOperatorId() : SYSTEM_OPERATOR_ID;
        String operatorName = StringUtils.hasText(req.getOperatorName()) ? req.getOperatorName() : SYSTEM_OPERATOR_NAME;
        // 手动触发执行
        return runTask(req.getTaskCode().trim(), ScheduleTriggerType.MANUAL.getCode(), operatorId, operatorName);
    }

    /**
     * 兼容仅传编码的调用。
     *
     * @param taskCode 任务编码
     * @return 执行结果
     */
    public ScheduledTaskResult executeTask(String taskCode) {
        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode(taskCode);
        return executeTask(req);
    }

    /**
     * 按序批量执行任务。
     *
     * @param req 批量请求
     * @return 各任务执行结果
     */
    public List<ScheduledTaskResult> executeTasks(ScheduledTaskReq req) {
        if (req == null || req.getTaskCodes() == null || req.getTaskCodes().isEmpty()) {
            throw new BizException("任务编码列表不能为空");
        }
        List<ScheduledTaskResult> results = new ArrayList<>();
        for (String code : req.getTaskCodes()) {
            try {
                ScheduledTaskReq one = new ScheduledTaskReq();
                one.setTaskCode(code);
                one.setOperatorId(req.getOperatorId());
                one.setOperatorName(req.getOperatorName());
                // 逐个手动执行
                results.add(executeTask(one));
            } catch (Exception e) {
                log.error("批量触发任务[{}]异常", code, e);
                results.add(ScheduledTaskResult.failure(
                        code, code, "触发失败: " + e.getMessage(), new Date(), 0L));
            }
        }
        return results;
    }

    /**
     * 分页查询执行历史。
     *
     * @param req 查询条件
     * @return 分页结果
     */
    public PageResult<ScheduledTaskRunLogDto> queryRunLogPage(ScheduledTaskReq req) {
        int pageIndex = req == null ? 1 : req.getPageIndex();
        int pageSize = req == null ? 20 : req.getPageSize();
        String taskCode = req == null ? null : req.getTaskCode();
        // 开启分页
        PageHelper.startPage(pageIndex, pageSize);
        // 查询历史列表
        List<SysScheduledTaskRunLogBo> list = scheduledTaskMapper.queryRunLogList(taskCode);
        PageInfo<SysScheduledTaskRunLogBo> pageInfo = new PageInfo<>(list);
        List<ScheduledTaskRunLogDto> dtoList = new ArrayList<>();
        if (list != null) {
            for (SysScheduledTaskRunLogBo bo : list) {
                // 转展示 DTO
                dtoList.add(toRunLogDto(bo));
            }
        }
        return new PageResult<>(dtoList, pageInfo.getTotal(), pageIndex, pageSize);
    }

    /**
     * 将代码任务种子同步到库表（仅补缺失，不覆盖已有配置）。
     */
    private void syncCodeTasksToDb() {
        for (RrsScheduledTask task : taskMap.values()) {
            SysScheduledTaskBo existing = scheduledTaskMapper.queryTaskByCode(task.getTaskCode());
            if (existing != null) {
                continue;
            }
            SysScheduledTaskBo seed = new SysScheduledTaskBo();
            seed.setTaskCode(task.getTaskCode());
            seed.setTaskName(task.getTaskName());
            seed.setDescription(task.getDescription());
            seed.setCronExpression(task.getDefaultCronExpression());
            seed.setScheduleEnabled(task.isDefaultScheduleEnabled() ? 1 : 0);
            seed.setParamJson(task.getDefaultParamJson());
            // 写入种子配置
            scheduledTaskMapper.addTask(seed);
            log.info("定时任务种子写入: {}", task.getTaskCode());
        }
    }

    /**
     * 按库表重新挂载全部已启用任务。
     */
    private void reloadAllSchedules() {
        for (String code : taskMap.keySet()) {
            // 先全部取消再按配置挂载
            dynamicTaskScheduler.cancel(code);
        }
        List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskList();
        if (list == null) {
            return;
        }
        for (SysScheduledTaskBo bo : list) {
            // 按单条配置挂载或取消
            applySchedule(bo.getTaskCode(), bo);
        }
    }

    /**
     * 根据配置挂载或取消调度。
     *
     * @param taskCode 任务编码
     * @param conf     库表配置
     */
    private void applySchedule(String taskCode, SysScheduledTaskBo conf) {
        if (conf == null || !taskMap.containsKey(taskCode)) {
            dynamicTaskScheduler.cancel(taskCode);
            return;
        }
        boolean enabled = Integer.valueOf(1).equals(conf.getScheduleEnabled());
        if (!enabled || !StringUtils.hasText(conf.getCronExpression())) {
            dynamicTaskScheduler.cancel(taskCode);
            return;
        }
        // 挂载 cron 回调
        dynamicTaskScheduler.schedule(taskCode, conf.getCronExpression(), () -> {
            try {
                // 定时触发执行
                runTask(taskCode, ScheduleTriggerType.CRON.getCode(), SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME);
            } catch (Exception e) {
                log.error("定时触发任务[{}]异常", taskCode, e);
            }
        });
    }

    /**
     * 执行任务并写最近结果 + 历史日志（同任务串行）。
     *
     * @param taskCode     任务编码
     * @param triggerType  触发方式 code
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 执行结果
     */
    private ScheduledTaskResult runTask(String taskCode, String triggerType,
                                        String operatorId, String operatorName) {
        // 校验代码实现已注册
        RrsScheduledTask task = requireCodeTask(taskCode);
        // 展示名优先读库（页面可改名称）
        String displayName = resolveTaskDisplayName(taskCode, task);
        Object lock = runLocks.computeIfAbsent(taskCode, k -> new Object());
        synchronized (lock) {
            log.info("触发定时任务: {} ({}) trigger={}", displayName, taskCode, triggerType);
            // 执行业务（逻辑在代码实现中；扩展参数由各实现自行读库）
            ScheduledTaskResult result = task.execute();
            // 持久化最近结果与历史
            persistRunResult(taskCode, displayName, result, triggerType, operatorId, operatorName);
            return result;
        }
    }

    /**
     * 持久化执行摘要与历史记录。
     *
     * @param taskCode     任务编码
     * @param displayName  展示名称（优先库表）
     * @param result       执行结果
     * @param triggerType  触发方式
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     */
    private void persistRunResult(String taskCode, String displayName, ScheduledTaskResult result,
                                  String triggerType, String operatorId, String operatorName) {
        try {
            String status = result != null && result.isSuccess()
                    ? ScheduleRunStatus.SUCCESS.getCode()
                    : ScheduleRunStatus.FAIL.getCode();
            String message = result != null ? result.getMessage() : "无结果";
            int affected = result != null ? result.getAffectedCount() : 0;
            long duration = result != null ? result.getDurationMs() : 0L;
            Date start = result != null && result.getStartTime() != null ? result.getStartTime() : new Date();
            Date end = result != null && result.getEndTime() != null ? result.getEndTime() : new Date();

            SysScheduledTaskBo last = new SysScheduledTaskBo();
            last.setTaskCode(taskCode);
            last.setLastRunStatus(status);
            last.setLastRunMessage(truncate(message, 1000));
            last.setLastRunTime(start);
            last.setLastAffectedCount(affected);
            last.setLastDurationMs(duration);
            last.setLastTriggerType(triggerType);
            // 更新主表最近执行摘要
            scheduledTaskMapper.editTaskLastRun(last);

            SysScheduledTaskRunLogBo logBo = new SysScheduledTaskRunLogBo();
            logBo.setTaskCode(taskCode);
            logBo.setTaskName(displayName);
            logBo.setTriggerType(triggerType);
            logBo.setRunStatus(status);
            logBo.setMessage(truncate(message, 1000));
            logBo.setAffectedCount(affected);
            logBo.setDurationMs(duration);
            logBo.setStartTime(start);
            logBo.setEndTime(end);
            logBo.setOperatorId(operatorId);
            logBo.setOperatorName(operatorName);
            // 写执行历史
            scheduledTaskMapper.addRunLog(logBo);
        } catch (Exception e) {
            log.warn("持久化任务执行结果失败: {}", taskCode, e);
        }
    }

    /**
     * 解析任务展示名称：库表优先，否则代码默认名。
     */
    private String resolveTaskDisplayName(String taskCode, RrsScheduledTask task) {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(taskCode);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName();
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败，使用代码默认: {}", taskCode);
        }
        return task != null ? task.getTaskName() : taskCode;
    }

    /**
     * 加载库表任务配置 Map。
     */
    private Map<String, SysScheduledTaskBo> loadDbTaskMap() {
        Map<String, SysScheduledTaskBo> map = new LinkedHashMap<>();
        try {
            List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskList();
            if (list != null) {
                for (SysScheduledTaskBo bo : list) {
                    map.put(bo.getTaskCode(), bo);
                }
            }
        } catch (Exception e) {
            log.warn("加载定时任务库表配置失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 合并代码实现与库表配置为展示 DTO。
     */
    private ScheduledTaskInfoDto toInfoDto(RrsScheduledTask task, SysScheduledTaskBo bo) {
        ScheduledTaskInfoDto dto = new ScheduledTaskInfoDto();
        String code = task != null ? task.getTaskCode() : (bo != null ? bo.getTaskCode() : null);
        dto.setTaskCode(code);
        dto.setCodeRegistered(task != null);
        if (bo != null) {
            // 库表配置优先（名称/描述等允许页面编辑后持久化）
            dto.setId(bo.getId());
            dto.setTaskName(StringUtils.hasText(bo.getTaskName())
                    ? bo.getTaskName()
                    : (task != null ? task.getTaskName() : code));
            dto.setDescription(bo.getDescription() != null
                    ? bo.getDescription()
                    : (task != null ? task.getDescription() : null));
            dto.setCronExpression(bo.getCronExpression());
            dto.setScheduleEnabled(Integer.valueOf(1).equals(bo.getScheduleEnabled()));
            dto.setParamJson(bo.getParamJson());
            dto.setLastRunStatus(bo.getLastRunStatus());
            dto.setLastRunMessage(bo.getLastRunMessage());
            dto.setLastRunTime(bo.getLastRunTime());
            dto.setLastAffectedCount(bo.getLastAffectedCount());
            dto.setLastDurationMs(bo.getLastDurationMs());
            dto.setLastTriggerType(bo.getLastTriggerType());
        } else if (task != null) {
            dto.setTaskName(task.getTaskName());
            dto.setDescription(task.getDescription());
            dto.setCronExpression(task.getDefaultCronExpression());
            dto.setScheduleEnabled(task.isDefaultScheduleEnabled());
            dto.setParamJson(task.getDefaultParamJson());
        }
        dto.setCurrentlyScheduled(code != null && dynamicTaskScheduler.isScheduled(code));
        return dto;
    }

    /**
     * 执行历史 Bo 转 DTO。
     */
    private ScheduledTaskRunLogDto toRunLogDto(SysScheduledTaskRunLogBo bo) {
        ScheduledTaskRunLogDto dto = new ScheduledTaskRunLogDto();
        dto.setId(bo.getId());
        dto.setTaskCode(bo.getTaskCode());
        dto.setTaskName(bo.getTaskName());
        dto.setTriggerType(bo.getTriggerType());
        dto.setRunStatus(bo.getRunStatus());
        dto.setMessage(bo.getMessage());
        dto.setAffectedCount(bo.getAffectedCount());
        dto.setDurationMs(bo.getDurationMs());
        dto.setStartTime(bo.getStartTime());
        dto.setEndTime(bo.getEndTime());
        dto.setOperatorId(bo.getOperatorId());
        dto.setOperatorName(bo.getOperatorName());
        return dto;
    }

    /**
     * 按编码取代码实现，不存在则抛业务异常。
     */
    private RrsScheduledTask requireCodeTask(String taskCode) {
        // 规范化编码
        String code = trimCode(taskCode);
        RrsScheduledTask task = taskMap.get(code);
        if (task == null) {
            throw new BizException("未注册的定时任务实现: " + taskCode);
        }
        return task;
    }

    /**
     * 校验并裁剪任务编码。
     */
    private String trimCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            throw new BizException("任务编码不能为空");
        }
        return taskCode.trim();
    }

    /**
     * 截断过长文案。
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

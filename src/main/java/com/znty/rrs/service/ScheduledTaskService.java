package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.EventType;
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
import java.util.regex.Pattern;

/**
 * 定时任务配置与调度服务
 * <p>
 * 库表维护配置（名称/说明/cron/启停/扩展参数）；业务类实现 {@link RrsScheduledTask}
 *（仅 taskCode + execute）。扩展新任务：① 页面新增配置 ② 新建实现类并部署。
 * </p>
 */
@Slf4j
@Service
public class ScheduledTaskService {

    /** 系统操作人 ID */
    private static final String SYSTEM_OPERATOR_ID = "0";
    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR_NAME = "系统";
    /** 任务编码：字母开头，字母数字下划线，2~64 位 */
    private static final Pattern TASK_CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,63}$");

    /** taskCode → 业务实现 */
    private final Map<String, RrsScheduledTask> taskMap;
    /** 同任务串行锁 */
    private final Map<String, Object> runLocks = new ConcurrentHashMap<>();

    /** 定时任务 Mapper */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 动态调度器 */
    @Resource
    private DynamicTaskScheduler dynamicTaskScheduler;

    /**
     * 构造时收集全部 {@link RrsScheduledTask} 实现，按 taskCode 注册到内存映射（编码不可重复）
     */
    public ScheduledTaskService(List<RrsScheduledTask> taskList) {
        Map<String, RrsScheduledTask> map = new LinkedHashMap<>();
        if (taskList != null) {
            for (RrsScheduledTask task : taskList) {
                if (task == null || !StringUtils.hasText(task.getTaskCode())) {
                    continue;
                }
                if (map.containsKey(task.getTaskCode())) {
                    throw new IllegalStateException("定时任务编码重复: " + task.getTaskCode());
                }
                map.put(task.getTaskCode(), task);
            }
        }
        this.taskMap = Collections.unmodifiableMap(map);
        log.info("定时任务实现已注册: {}", this.taskMap.keySet());
    }

    /**
     * 应用启动后按库表配置挂载已启用且已有业务实现的定时任务
     */
    @PostConstruct
    public void initAfterStartup() {
        try {
            // 按库表重挂调度
            reloadAllSchedules();
        } catch (Exception e) {
            log.warn("定时任务启动挂载失败（表可能未初始化）: {}", e.getMessage());
        }
    }

    /**
     * 查询全部有效定时任务配置列表（仅读库表），并补充是否已注册实现、是否已挂载调度
     */
    public List<ScheduledTaskInfoDto> queryTaskList() {
        List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskList();
        List<ScheduledTaskInfoDto> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (SysScheduledTaskBo bo : list) {
            // 转展示 DTO
            result.add(toInfoDto(bo));
        }
        return result;
    }

    /**
     * 分页查询定时任务配置，支持名称/编码关键字与调度启停筛选
     *
     * @param req 分页与筛选条件
     */
    public PageResult<ScheduledTaskInfoDto> queryTaskPage(ScheduledTaskReq req) {
        if (req == null) {
            req = new ScheduledTaskReq();
        }
        // 开启分页
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询列表
        List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskPage(req);
        // 获取分页信息
        PageInfo<SysScheduledTaskBo> pageInfo = new PageInfo<>(list);
        List<ScheduledTaskInfoDto> dtoList = new ArrayList<>();
        if (list != null) {
            for (SysScheduledTaskBo bo : list) {
                // 转展示 DTO 并补充实现注册 / 调度挂载状态
                dtoList.add(toInfoDto(bo));
            }
        }
        return new PageResult<>(dtoList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按任务编码查询单条定时任务配置详情，不存在则抛业务异常
     */
    public ScheduledTaskInfoDto queryTask(String taskCode) {
        // 校验并加载配置
        return toInfoDto(requireConfig(taskCode));
    }

    /**
     * 新增定时任务配置并写审计；编码唯一，保存成功后若有业务实现则按 cron 挂载调度
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto addTask(ScheduledTaskReq req) {
        // 组装并校验新增字段
        SysScheduledTaskBo bo = buildSaveBo(req, null);
        if (scheduledTaskMapper.queryTaskByCode(bo.getTaskCode()) != null) {
            throw new BizException("任务编码已存在: " + bo.getTaskCode());
        }
        // 写入库表
        scheduledTaskMapper.addTask(bo);
        // 回查并挂载
        SysScheduledTaskBo latest = requireConfig(bo.getTaskCode());
        // 审计 oprt_type 存英文：INSERT / UPDATE / DELETE（与流程等 _evt 一致）
        writeEvent(latest, req, EventType.INSERT.getCode());
        // 有实现则挂载调度
        applySchedule(latest);
        return toInfoDto(latest);
    }

    /**
     * 修改定时任务配置（名称、说明、cron、启停、扩展参数），写审计后按最新配置重挂或取消调度
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto editTask(ScheduledTaskReq req) {
        // 加载已有配置
        SysScheduledTaskBo existing = requireConfig(req == null ? null : req.getTaskCode());
        // 组装保存对象
        SysScheduledTaskBo bo = buildSaveBo(req, existing);
        // 更新库表
        scheduledTaskMapper.editTask(bo);
        // 审计 oprt_type：UPDATE
        writeEvent(bo, req, EventType.UPDATE.getCode());
        // 按本次保存值重挂调度
        applySchedule(bo);
        // 回查展示
        return toInfoDto(requireConfig(bo.getTaskCode()));
    }

    /**
     * 逻辑删除定时任务配置，取消调度并写审计，返回删除前快照
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto deleteTask(ScheduledTaskReq req) {
        // 加载配置
        SysScheduledTaskBo existing = requireConfig(req == null ? null : req.getTaskCode());
        // 取消调度
        dynamicTaskScheduler.cancel(existing.getTaskCode());
        // 逻辑删除
        scheduledTaskMapper.deleteTaskSoft(existing.getTaskCode());
        existing.setIsDeleted(1);
        existing.setScheduleEnabled(0);
        // 审计 oprt_type：DELETE
        writeEvent(existing, req, EventType.DELETE.getCode());
        return toInfoDto(existing);
    }

    /**
     * 手动触发执行指定任务（需已有库表配置与业务实现），并写入最近执行摘要与执行历史
     */
    public ScheduledTaskResult executeTask(ScheduledTaskReq req) {
        // 必须有库表配置
        SysScheduledTaskBo conf = requireConfig(req == null ? null : req.getTaskCode());
        String operatorId = (req != null && StringUtils.hasText(req.getOperatorId()))
                ? req.getOperatorId() : SYSTEM_OPERATOR_ID;
        String operatorName = (req != null && StringUtils.hasText(req.getOperatorName()))
                ? req.getOperatorName() : SYSTEM_OPERATOR_NAME;
        // 执行
        return runTask(conf.getTaskCode(), ScheduleTriggerType.MANUAL.getCode(), operatorId, operatorName);
    }

    /**
     * 分页查询定时任务执行历史，可按任务编码筛选
     */
    public PageResult<ScheduledTaskRunLogDto> queryRunLogPage(ScheduledTaskReq req) {
        int pageIndex = req == null ? 1 : req.getPageIndex();
        int pageSize = req == null ? 20 : req.getPageSize();
        String taskCode = req == null ? null : req.getTaskCode();
        // 开启分页
        PageHelper.startPage(pageIndex, pageSize);
        // 查询列表
        List<SysScheduledTaskRunLogBo> list = scheduledTaskMapper.queryRunLogList(taskCode);
        PageInfo<SysScheduledTaskRunLogBo> pageInfo = new PageInfo<>(list);
        List<ScheduledTaskRunLogDto> dtoList = new ArrayList<>();
        if (list != null) {
            for (SysScheduledTaskRunLogBo bo : list) {
                // 转历史展示 DTO
                dtoList.add(toRunLogDto(bo));
            }
        }
        return new PageResult<>(dtoList, pageInfo.getTotal(), pageIndex, pageSize);
    }

    // ---------- 内部 ----------

    /**
     * 校验入参并组装待保存配置；existing 为空表示新增（校验编码格式），非空表示修改（编码沿用原值）
     */
    private SysScheduledTaskBo buildSaveBo(ScheduledTaskReq req, SysScheduledTaskBo existing) {
        if (req == null) {
            throw new BizException("请求不能为空");
        }
        // 新增时校验编码格式；修改时沿用原编码
        String taskCode = existing != null ? existing.getTaskCode() : trimCode(req.getTaskCode());
        if (existing == null && !TASK_CODE_PATTERN.matcher(taskCode).matches()) {
            throw new BizException("任务编码须以字母开头，仅含字母数字下划线，长度 2~64");
        }
        String taskName = StringUtils.hasText(req.getTaskName())
                ? req.getTaskName().trim()
                : (existing != null ? existing.getTaskName() : null);
        if (!StringUtils.hasText(taskName)) {
            throw new BizException("任务名称不能为空");
        }
        if (taskName.length() > 128) {
            throw new BizException("任务名称不能超过 128 字");
        }
        String cron = req.getCronExpression() != null
                ? req.getCronExpression().trim()
                : (existing != null ? existing.getCronExpression() : null);
        if (!StringUtils.hasText(cron) || !dynamicTaskScheduler.isValidCron(cron)) {
            throw new BizException("cron 表达式非法，示例：0 0 2 * * ?");
        }
        String description = req.getDescription() != null
                ? req.getDescription().trim()
                : (existing != null ? existing.getDescription() : null);
        if (description != null && description.length() > 500) {
            throw new BizException("任务说明不能超过 500 字");
        }
        boolean enabled = req.getScheduleEnabled() != null
                ? req.getScheduleEnabled()
                : (existing != null && Integer.valueOf(1).equals(existing.getScheduleEnabled()));
        String paramJson = req.getParamJson() != null
                ? req.getParamJson().trim()
                : (existing != null ? existing.getParamJson() : null);

        SysScheduledTaskBo bo = new SysScheduledTaskBo();
        bo.setTaskCode(taskCode);
        bo.setTaskName(taskName);
        bo.setDescription(description);
        bo.setCronExpression(cron);
        bo.setScheduleEnabled(enabled ? 1 : 0);
        bo.setParamJson(paramJson);
        return bo;
    }

    /**
     * 按库表重新挂载全部可调度任务：先取消已注册实现上的挂载，再逐条按配置挂载
     */
    private void reloadAllSchedules() {
        List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskList();
        // 先取消全部已知实现的挂载
        for (String code : new ArrayList<>(taskMap.keySet())) {
            dynamicTaskScheduler.cancel(code);
        }
        if (list == null) {
            return;
        }
        for (SysScheduledTaskBo bo : list) {
            // 按单条配置挂载或取消
            applySchedule(bo);
        }
    }

    /**
     * 按单条配置挂载或取消调度：仅当「已启用 + cron 合法 + 已有业务实现」时挂载，否则取消
     */
    private void applySchedule(SysScheduledTaskBo conf) {
        if (conf == null || !StringUtils.hasText(conf.getTaskCode())) {
            return;
        }
        String taskCode = conf.getTaskCode();
        boolean canRun = Integer.valueOf(1).equals(conf.getScheduleEnabled())
                && StringUtils.hasText(conf.getCronExpression())
                && taskMap.containsKey(taskCode);
        if (!canRun) {
            // 不满足挂载条件则取消
            dynamicTaskScheduler.cancel(taskCode);
            return;
        }
        // 按 cron 挂载调度
        dynamicTaskScheduler.schedule(taskCode, conf.getCronExpression(), () -> {
            try {
                // 定时触发执行
                runTask(taskCode, ScheduleTriggerType.CRON.getCode(), SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME);
            } catch (Exception e) {
                log.error("定时任务执行失败: {}", taskCode, e);
            }
        });
    }

    /**
     * 按任务编码串行执行业务实现，并持久化最近执行摘要与执行历史
     */
    private ScheduledTaskResult runTask(String taskCode, String triggerType,
                                        String operatorId, String operatorName) {
        // 取业务实现
        RrsScheduledTask task = requireImpl(taskCode);
        // 展示名读库
        String displayName = resolveTaskName(taskCode);
        Object lock = runLocks.computeIfAbsent(taskCode, k -> new Object());
        synchronized (lock) {
            log.info("执行定时任务 {} ({}) trigger={}", displayName, taskCode, triggerType);
            // 执行业务
            ScheduledTaskResult result = task.execute();
            // 写最近结果与历史
            persistRunResult(taskCode, displayName, result, triggerType, operatorId, operatorName);
            return result;
        }
    }

    /**
     * 将本次执行结果写入主表最近执行摘要与执行历史表（失败仅打日志，不向上抛）
     */
    private void persistRunResult(String taskCode, String displayName, ScheduledTaskResult result,
                                  String triggerType, String operatorId, String operatorName) {
        try {
            boolean ok = result != null && result.isSuccess();
            String status = ok ? ScheduleRunStatus.SUCCESS.getCode() : ScheduleRunStatus.FAIL.getCode();
            String message = result != null ? result.getMessage() : "无结果";
            int affected = result != null ? result.getAffectedCount() : 0;
            long duration = result != null ? result.getDurationMs() : 0L;
            Date start = result != null && result.getStartTime() != null ? result.getStartTime() : new Date();
            Date end = result != null && result.getEndTime() != null ? result.getEndTime() : new Date();

            SysScheduledTaskBo last = new SysScheduledTaskBo();
            last.setTaskCode(taskCode);
            last.setLastRunStatus(status);
            // 截断过长说明
            last.setLastRunMessage(truncate(message, 1000));
            last.setLastRunTime(start);
            last.setLastAffectedCount(affected);
            last.setLastDurationMs(duration);
            last.setLastTriggerType(triggerType);
            // 更新主表最近执行
            scheduledTaskMapper.editTaskLastRun(last);

            SysScheduledTaskRunLogBo logBo = new SysScheduledTaskRunLogBo();
            logBo.setTaskCode(taskCode);
            logBo.setTaskName(displayName);
            logBo.setTriggerType(triggerType);
            logBo.setRunStatus(status);
            logBo.setMessage(truncate(message, 1000));
            // 过程日志写入历史（截断防过大）
            String detailLog = result != null ? result.getDetailLog() : null;
            logBo.setDetailLog(truncate(detailLog, 20000));
            logBo.setAffectedCount(affected);
            logBo.setDurationMs(duration);
            logBo.setStartTime(start);
            logBo.setEndTime(end);
            logBo.setOperatorId(operatorId);
            logBo.setOperatorName(operatorName);
            // 写执行历史
            scheduledTaskMapper.addRunLog(logBo);
        } catch (Exception e) {
            log.warn("持久化执行结果失败: {}", taskCode, e);
        }
    }

    /**
     * 写入配置变更审计事件
     * <p>
     * {@code oprtType} 统一存英文：{@link EventType#INSERT} / {@link EventType#UPDATE} / {@link EventType#DELETE}。
     * </p>
     */
    private void writeEvent(SysScheduledTaskBo bo, ScheduledTaskReq req, String oprtType) {
        String opterId = (req != null && StringUtils.hasText(req.getOperatorId()))
                ? req.getOperatorId() : SYSTEM_OPERATOR_ID;
        // 插入审计事件
        scheduledTaskMapper.addTaskEvent(bo, opterId, oprtType);
    }

    /**
     * 按编码加载未删除的任务配置，不存在则抛业务异常
     */
    private SysScheduledTaskBo requireConfig(String taskCode) {
        // 规范化编码
        String code = trimCode(taskCode);
        SysScheduledTaskBo bo = scheduledTaskMapper.queryTaskByCode(code);
        if (bo == null) {
            throw new BizException("未找到定时任务: " + code);
        }
        return bo;
    }

    /**
     * 按编码取已注册的业务实现，未实现则抛业务异常
     */
    private RrsScheduledTask requireImpl(String taskCode) {
        // 规范化编码后查注册表
        RrsScheduledTask task = taskMap.get(trimCode(taskCode));
        if (task == null) {
            throw new BizException("任务[" + taskCode + "]尚未注册业务实现，请新增 RrsScheduledTask 实现类");
        }
        return task;
    }

    /**
     * 从库表读取任务展示名称，读库失败或名称为空时回退为任务编码
     */
    private String resolveTaskName(String taskCode) {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(taskCode);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName();
            }
        } catch (Exception ignored) {
            // 读库失败时回退编码
        }
        return taskCode;
    }

    /**
     * 将库表配置转换为列表/详情展示 DTO，并补充实现注册与调度挂载状态
     */
    private ScheduledTaskInfoDto toInfoDto(SysScheduledTaskBo bo) {
        ScheduledTaskInfoDto dto = new ScheduledTaskInfoDto();
        if (bo == null) {
            return dto;
        }
        String code = bo.getTaskCode();
        dto.setId(bo.getId());
        dto.setTaskCode(code);
        dto.setTaskName(bo.getTaskName());
        dto.setDescription(bo.getDescription());
        dto.setCronExpression(bo.getCronExpression());
        dto.setScheduleEnabled(Integer.valueOf(1).equals(bo.getScheduleEnabled()));
        dto.setParamJson(bo.getParamJson());
        // 已注册实现时带回该任务的扩展参数填写说明（通用字段，不写死具体任务）
        if (code != null && taskMap.containsKey(code)) {
            dto.setParamHelp(taskMap.get(code).getParamHelp());
        }
        dto.setCodeRegistered(code != null && taskMap.containsKey(code));
        dto.setCurrentlyScheduled(code != null && dynamicTaskScheduler.isScheduled(code));
        dto.setLastRunStatus(bo.getLastRunStatus());
        dto.setLastRunMessage(bo.getLastRunMessage());
        dto.setLastRunTime(bo.getLastRunTime());
        dto.setLastAffectedCount(bo.getLastAffectedCount());
        dto.setLastDurationMs(bo.getLastDurationMs());
        dto.setLastTriggerType(bo.getLastTriggerType());
        return dto;
    }

    /**
     * 将执行历史 Bo 转换为前端展示 DTO
     */
    private ScheduledTaskRunLogDto toRunLogDto(SysScheduledTaskRunLogBo bo) {
        ScheduledTaskRunLogDto dto = new ScheduledTaskRunLogDto();
        dto.setId(bo.getId());
        dto.setTaskCode(bo.getTaskCode());
        dto.setTaskName(bo.getTaskName());
        dto.setTriggerType(bo.getTriggerType());
        dto.setRunStatus(bo.getRunStatus());
        dto.setMessage(bo.getMessage());
        dto.setDetailLog(bo.getDetailLog());
        dto.setAffectedCount(bo.getAffectedCount());
        dto.setDurationMs(bo.getDurationMs());
        dto.setStartTime(bo.getStartTime());
        dto.setEndTime(bo.getEndTime());
        dto.setOperatorId(bo.getOperatorId());
        dto.setOperatorName(bo.getOperatorName());
        return dto;
    }

    /**
     * 规范化任务编码（去首尾空白），为空则抛业务异常
     */
    private String trimCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            throw new BizException("任务编码不能为空");
        }
        return taskCode.trim();
    }

    /**
     * 将文本截断到指定最大长度，超出部分丢弃
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

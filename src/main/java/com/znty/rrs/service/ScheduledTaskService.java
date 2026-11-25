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
 * 维护定时任务配置并调度任务执行
 *
 * 启动后按数据库中的 Cron、启停状态和扩展参数注册任务，修改配置时同步更新调度
 * 定时触发和页面手动执行都按任务编码查找 {@link RrsScheduledTask} 实现，通过 {@link JobLockService} 避免多个实例重复执行
 * 每次执行完成后保存执行结果和过程日志，供定时任务管理页面查询
 */
@Slf4j
@Service
public class ScheduledTaskService {

    /** 系统操作人 ID */
    private static final String SYSTEM_OPERATOR_ID = "0";

    /** 系统操作人名称 */
    private static final String SYSTEM_OPERATOR_NAME = "系统";

    /** 任务编码：字母开头，字母数字下划线，2~50 位 */
    private static final Pattern TASK_CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,49}$");

    /** MySQL 命名锁前缀 */
    private static final String LOCK_NAME_PREFIX = "rrs_scheduled_";

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

    /** 多实例任务锁 */
    @Resource
    private JobLockService jobLockService;

    /**
     * 注册定时任务管理可执行的任务
     *
     * 执行顺序：
     * 1. 按编码收集可用的 {@link RrsScheduledTask} 实现，发现重复编码时终止初始化
     * 2. 保存只读任务映射并记录已注册的编码
     *
     * @param taskList Spring 提供的定时任务实现
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
     *
     * 执行顺序：
     * 1. 加载任务配置并重新挂载调度，加载失败时记警告日志
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
     *
     * 执行顺序：
     * 1. 读取有效的任务配置，查不到记录时返回空列表
     * 2. 逐条转换为页面信息并补入实现注册和调度挂载状态
     *
     * @return 定时任务配置及运行信息列表
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
     * 执行顺序：
     * 1. 使用请求条件分页查询任务配置，缺省时使用默认分页
     * 2. 补充任务实现注册和调度挂载状态
     * 3. 返回本页记录和查询总条数
     *
     * @param req 分页与筛选条件
     * @return 定时任务配置及运行信息及分页信息
     */
    public PageResult<ScheduledTaskInfoDto> queryTaskPage(ScheduledTaskReq req) {
        if (req == null) {
            req = new ScheduledTaskReq();
        }
        // 1. 使用请求条件分页查询任务配置，缺省时使用默认分页
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());

        List<SysScheduledTaskBo> list = scheduledTaskMapper.queryTaskPage(req);

        PageInfo<SysScheduledTaskBo> pageInfo = new PageInfo<>(list);
        // 2. 补充任务实现注册和调度挂载状态
        List<ScheduledTaskInfoDto> dtoList = new ArrayList<>();
        if (list != null) {
            for (SysScheduledTaskBo bo : list) {
                // 转展示 DTO 并补充实现注册 / 调度挂载状态
                dtoList.add(toInfoDto(bo));
            }
        }
        // 3. 返回本页记录和查询总条数
        return new PageResult<>(dtoList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按任务编码查询单条定时任务配置详情，不存在则抛业务异常
     *
     * 执行顺序：
     * 1. 将库表配置转换为列表/详情展示 DTO，并补充实现注册与调度挂载状态
     *
     * @param taskCode 任务编码
     * @return 定时任务配置及运行信息
     */
    public ScheduledTaskInfoDto queryTask(String taskCode) {
        // 校验并加载配置
        return toInfoDto(requireConfig(taskCode));
    }

    /**
     * 新增定时任务配置并写审计；编码唯一，保存成功后若有业务实现则按 cron 挂载调度
     *
     * 执行顺序：
     * 1. 校验新增任务配置，检查任务编码是否重复
     * 2. 保存任务配置
     * 3. 回查配置并写入新增审计
     * 4. 挂载任务调度并返回登记信息
     *
     * @param req 分页与筛选条件
     * @return 定时任务配置及运行信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto addTask(ScheduledTaskReq req) {
        // 1. 校验新增任务配置，检查任务编码是否重复
        SysScheduledTaskBo bo = buildSaveBo(req, null);
        if (scheduledTaskMapper.queryTaskByCode(bo.getTaskCode()) != null) {
            throw new BizException("任务编码已存在: " + bo.getTaskCode());
        }
        // 2. 保存任务配置
        scheduledTaskMapper.addTask(bo);
        // 3. 回查配置并写入新增审计
        SysScheduledTaskBo latest = requireConfig(bo.getTaskCode());
        // 审计 oprt_type 存英文：INSERT / UPDATE / DELETE（与流程等 _evt 一致）
        writeEvent(latest, req, EventType.INSERT.getCode());
        // 4. 挂载任务调度并返回登记信息
        applySchedule(latest);
        // 将库表配置转换为列表/详情展示 DTO，并补充实现注册与调度挂载状态
        return toInfoDto(latest);
    }

    /**
     * 修改定时任务配置（名称、说明、cron、启停、扩展参数），写审计后按最新配置重挂或取消调度
     *
     * 执行顺序：
     * 1. 加载已有配置
     * 2. 组装保存对象
     * 3. 更新库表
     * 4. 审计 oprt_type：UPDATE
     * 5. 按本次保存值重挂调度
     * 6. 回查展示
     *
     * @param req 分页与筛选条件
     * @return 定时任务配置及运行信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto editTask(ScheduledTaskReq req) {
        // 1. 加载已有配置
        SysScheduledTaskBo existing = requireConfig(req == null ? null : req.getTaskCode());
        // 2. 组装保存对象
        SysScheduledTaskBo bo = buildSaveBo(req, existing);
        // 3. 更新库表
        scheduledTaskMapper.editTask(bo);
        // 4. 审计 oprt_type：UPDATE
        writeEvent(bo, req, EventType.UPDATE.getCode());
        // 5. 按本次保存值重挂调度
        applySchedule(bo);
        // 6. 回查展示
        return toInfoDto(requireConfig(bo.getTaskCode()));
    }

    /**
     * 逻辑删除定时任务配置，取消调度并写审计，返回删除前快照
     *
     * 执行顺序：
     * 1. 加载配置
     * 2. 取消调度
     * 3. 逻辑删除
     * 4. 审计 oprt_type：DELETE
     *
     * @param req 分页与筛选条件
     * @return 定时任务配置及运行信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskInfoDto deleteTask(ScheduledTaskReq req) {
        // 1. 加载配置
        SysScheduledTaskBo existing = requireConfig(req == null ? null : req.getTaskCode());
        // 2. 取消调度
        dynamicTaskScheduler.cancel(existing.getTaskCode());
        // 3. 逻辑删除
        scheduledTaskMapper.deleteTaskSoft(existing.getTaskCode());
        existing.setIsDeleted(1);
        existing.setScheduleEnabled(0);
        // 4. 审计 oprt_type：DELETE
        writeEvent(existing, req, EventType.DELETE.getCode());
        // 将库表配置转换为列表/详情展示 DTO，并补充实现注册与调度挂载状态
        return toInfoDto(existing);
    }

    /**
     * 手动触发执行指定任务（需已有库表配置与业务实现），并写入最近执行摘要与执行历史
     *
     * 执行顺序：
     * 1. 必须有库表配置
     *
     * @param req 分页与筛选条件
     * @return 定时任务结果
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
     *
     * 执行顺序：
     * 1. 使用请求条件分页查询执行记录，缺省时使用默认分页
     * 2. 转换为执行记录列表所用的数据
     * 3. 返回本页记录和查询总条数
     *
     * @param req 分页与筛选条件
     * @return 定时任务执行历史及分页信息
     */
    public PageResult<ScheduledTaskRunLogDto> queryRunLogPage(ScheduledTaskReq req) {
        int pageIndex = req == null ? 1 : req.getPageIndex();
        int pageSize = req == null ? 20 : req.getPageSize();
        String taskCode = req == null ? null : req.getTaskCode();
        // 1. 使用请求条件分页查询执行记录，缺省时使用默认分页
        PageHelper.startPage(pageIndex, pageSize);

        List<SysScheduledTaskRunLogBo> list = scheduledTaskMapper.queryRunLogPage(taskCode);
        PageInfo<SysScheduledTaskRunLogBo> pageInfo = new PageInfo<>(list);
        // 2. 转换为执行记录列表所用的数据
        List<ScheduledTaskRunLogDto> dtoList = new ArrayList<>();
        if (list != null) {
            for (SysScheduledTaskRunLogBo bo : list) {
                // 转历史展示 DTO
                dtoList.add(toRunLogDto(bo));
            }
        }
        // 3. 返回本页记录和查询总条数
        return new PageResult<>(dtoList, pageInfo.getTotal(), pageIndex, pageSize);
    }

    // ---------- 内部 ----------

    /**
     * 校验入参并组装待保存配置；existing 为空表示新增（校验编码格式），非空表示修改（编码沿用原值）
     *
     * 执行顺序：
     * 1. 检查请求，新增时校验新编码，修改时沿用原编码
     * 2. 读取任务名称并补充任务范围，名称缺失或超过 128 个字符时终止处理
     * 3. 检查 Cron 表达式，说明超过 500 字时终止处理
     * 4. 按新请求或原配置设置调度开关和扩展参数，再返回保存对象
     *
     * @param req 任务编码、名称、Cron 和扩展参数等保存信息
     * @param existing 修改时沿用的原配置，新增时为 null
     * @return 定时任务配置记录
     */
    private SysScheduledTaskBo buildSaveBo(ScheduledTaskReq req, SysScheduledTaskBo existing) {
        if (req == null) {
            throw new BizException("请求不能为空");
        }
        // 新增时校验编码格式；修改时沿用原编码
        String taskCode = existing != null ? existing.getTaskCode() : trimCode(req.getTaskCode());
        if (existing == null && !TASK_CODE_PATTERN.matcher(taskCode).matches()) {
            throw new BizException("任务编码须以字母开头，仅含字母数字下划线，长度 2~50");
        }
        String taskName = StringUtils.hasText(req.getTaskName())
                ? req.getTaskName().trim()
                : (existing != null ? existing.getTaskName() : null);
        if (!StringUtils.hasText(taskName)) {
            throw new BizException("任务名称不能为空");
        }
        taskName = ScheduledAdjustLogHelper.withTaskScope(taskCode, taskName);
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
     *
     * 执行顺序：
     * 1. 查询全部有效定时任务配置列表（仅读库表），并补充是否已注册实现、是否已挂载调度
     * 2. 先取消全部已知实现的挂载
     * 3. 按单条配置挂载或取消调度：仅当「已启用 + cron 合法 + 已有业务实现」时挂载，否则取消
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
     *
     * 执行顺序：
     * 1. 按 cron 挂载调度
     * 2. 按单条配置挂载或取消调度：仅当「已启用 + cron 合法 + 已有业务实现」时挂载，否则取消
     *
     * @param conf 该任务保存的调度配置
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
                log.error("定时任务触发失败: {}", taskCode, e);
            }
        });
    }

    /**
     * 按任务编码串行执行业务实现
     *
     * 执行顺序：
     * 1. 查找业务实现并识别触发方式
     * 2. 获取本机锁和 MySQL 命名锁
     * 3. 持锁读取最新配置，未启用的定时触发直接跳过
     * 4. 执行业务并保存最近执行摘要与执行历史
     *
     * @param taskCode 任务编码
     * @param triggerType 任务触发类型编码
     * @param operatorId 手动触发任务的操作人 ID
     * @param operatorName 操作人名称
     * @return 本次任务的执行结果，定时触发因配置未启用、配置缺失或其他节点持锁而跳过时返回 null
     */
    private ScheduledTaskResult runTask(String taskCode, String triggerType,
                                        String operatorId, String operatorName) {
        // 1. 查找业务实现并识别触发方式
        RrsScheduledTask task = requireImpl(taskCode);
        boolean cronTriggered = ScheduleTriggerType.CRON.getCode().equals(triggerType);
        // 本机锁只负责同一 JVM 内同任务串行；保留它可使本机并发触发按顺序执行，
        // 避免它们在尚未执行时就相互判定为“其他节点正在执行”
        // 2. 获取本机锁和 MySQL 命名锁
        Object lock = runLocks.computeIfAbsent(taskCode, k -> new Object());
        synchronized (lock) {
            // 回调在当前线程同步执行，数组仅用于从 Runnable 回传本次执行结果
            ScheduledTaskResult[] resultHolder = new ScheduledTaskResult[1];
            // MySQL 命名锁负责多 JVM / 多机器互斥；等待 0 秒，未获锁立即返回，
            // 不占用动态调度线程。任务执行和结果落库均置于持锁回调内，避免其他实例
            // 在业务已完成但历史尚未写入的窗口内重复执行
            boolean acquired = jobLockService.executeWithLock(buildLockName(taskCode), 0, () -> {
                // 持锁后读取库表配置，避免其他节点保留的旧调度在关闭后继续执行
                // 3. 持锁读取最新配置，未启用的定时触发直接跳过
                SysScheduledTaskBo config = queryRunConfig(taskCode, cronTriggered);
                if (cronTriggered && (config == null || !Integer.valueOf(1).equals(config.getScheduleEnabled()))) {
                    log.info("定时任务 {} 未启用或配置不存在，本次跳过", taskCode);
                    return;
                }
                String configuredName = config != null && StringUtils.hasText(config.getTaskName())
                        ? ScheduledAdjustLogHelper.withTaskScope(taskCode, config.getTaskName()) : null;
                String displayName = configuredName == null ? taskCode : configuredName;
                log.info("执行定时任务 {} ({}) trigger={}", displayName, taskCode, triggerType);
                Date startTime = new Date();
                try {
                    // 4. 执行业务并保存最近执行摘要与执行历史
                    resultHolder[0] = ScheduledAdjustLogHelper.runWithTaskName(configuredName, task::execute);
                    // 将本次执行结果写入主表最近执行摘要与执行历史表（失败仅打日志，不向上抛）
                    persistRunResult(taskCode, displayName, resultHolder[0], triggerType, operatorId, operatorName);
                } catch (RuntimeException | Error e) {
                    // 未捕获异常也要记录失败结果，再保留原异常继续向上抛出
                    long duration = System.currentTimeMillis() - startTime.getTime();
                    ScheduledTaskResult failure = ScheduledTaskResult.failure(taskCode, displayName,
                            // 生成异常摘要，异常消息为空时回退到异常类型
                            "执行异常: " + buildExceptionMessage(e), startTime, duration);
                    // 将本次执行结果写入主表最近执行摘要与执行历史表（失败仅打日志，不向上抛）
                    persistRunResult(taskCode, displayName, failure, triggerType, operatorId, operatorName);
                    throw e;
                }
            });
            if (!acquired) {
                // 手动触发需要明确告知操作者；不写执行历史，也不覆盖最近执行摘要
                if (ScheduleTriggerType.MANUAL.getCode().equals(triggerType)) {
                    throw new BizException("任务正在其他节点执行");
                }
                // cron 触发由其他实例执行时仅记录跳过日志，不写执行历史或最近执行摘要
                log.info("定时任务 {} 正在其他节点执行，已跳过", taskCode);
                return null;
            }
            // 已获取分布式锁时，返回持锁回调内产生的业务执行结果
            return resultHolder[0];
        }
    }

    /**
     * 组装 MySQL 命名锁。任务编码最多 50 字符，加上 14 字符前缀后总长不超过 64
     *
     * 执行顺序：
     * 1. 组装 MySQL 命名锁
     *
     * @param taskCode 任务编码
     * @return MySQL 命名锁
     */
    private String buildLockName(String taskCode) {
        return LOCK_NAME_PREFIX + taskCode;
    }

    /**
     * 生成异常摘要，异常消息为空时回退到异常类型
     *
     * 执行顺序：
     * 1. 检查文本
     *
     * @param throwable 本次任务抛出的异常
     * @return 异常摘要，异常消息为空时回退到异常类型
     */
    private String buildExceptionMessage(Throwable throwable) {
        return StringUtils.hasText(throwable.getMessage())
                ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    /**
     * 将本次执行结果写入主表最近执行摘要与执行历史表（失败仅打日志，不向上抛）
     *
     * 执行顺序：
     * 1. 更新任务最近一次执行
     * 2. 新增执行日志
     *
     * @param taskCode 任务编码
     * @param displayName 展示名称
     * @param result 本次操作的结果对象
     * @param triggerType 本次执行的触发类型编码，用于保存最近摘要和执行历史
     * @param operatorId 触发本次任务的操作人 ID，定时触发时使用系统操作人 ID
     * @param operatorName 触发本次任务的操作人名称，定时触发时使用系统操作人名称
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
            // 将文本截断到指定最大长度，超出部分丢弃
            logBo.setTaskName(truncate(displayName, 128));
            logBo.setTriggerType(triggerType);
            logBo.setRunStatus(status);
            // 将文本截断到指定最大长度，超出部分丢弃
            logBo.setMessage(truncate(message, 1000));
            // 过程日志写入历史（截断防过大）
            String detailLog = result != null ? result.getDetailLog() : null;
            // 将文本截断到指定最大长度，超出部分丢弃
            logBo.setDetailLog(truncate(detailLog, 30000));
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
     *
     * {@code oprtType} 统一存英文：{@link EventType#INSERT} / {@link EventType#UPDATE} / {@link EventType#DELETE}
     *
     * 执行顺序：
     * 1. 插入审计事件
     *
     * @param bo 待转换的数据库记录
     * @param req 分页与筛选条件
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     */
    private void writeEvent(SysScheduledTaskBo bo, ScheduledTaskReq req, String oprtType) {
        String opterId = (req != null && StringUtils.hasText(req.getOperatorId()))
                ? req.getOperatorId() : SYSTEM_OPERATOR_ID;
        // 插入审计事件
        scheduledTaskMapper.addTaskEvent(bo, opterId, oprtType);
    }

    /**
     * 按编码加载未删除的任务配置，不存在则抛业务异常
     *
     * 执行顺序：
     * 1. 规范化编码
     * 2. 按任务编码查询任务配置
     *
     * @param taskCode 任务编码
     * @return 定时任务配置记录
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
     *
     * 执行顺序：
     * 1. 规范化编码后查注册表
     *
     * @param taskCode 任务编码
     * @return 任务编码对应的执行器
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
     * 读取本次执行使用的最新任务配置
     *
     * 执行顺序：
     * 1. 按任务编码读取未删除的配置
     * 2. 读取失败时，定时触发抛出异常并跳过本轮，手动触发沿用默认任务名称
     *
     * @param taskCode 任务编码
     * @param cronTriggered 是否为定时触发
     * @return 任务配置，配置不存在或手动触发时读取失败则为空
     */
    private SysScheduledTaskBo queryRunConfig(String taskCode, boolean cronTriggered) {
        try {
            return scheduledTaskMapper.queryTaskByCode(taskCode);
        } catch (RuntimeException e) {
            if (cronTriggered) {
                throw e;
            }
            return null;
        }
    }

    /**
     * 将库表配置转换为列表/详情展示 DTO，并补充实现注册与调度挂载状态
     *
     * 执行顺序：
     * 1. 已注册实现时提供参数说明和配置页预填值
     *
     * @param bo 待转换的数据库记录
     * @return 定时任务配置及运行信息
     */
    private ScheduledTaskInfoDto toInfoDto(SysScheduledTaskBo bo) {
        ScheduledTaskInfoDto dto = new ScheduledTaskInfoDto();
        if (bo == null) {
            return dto;
        }
        String code = bo.getTaskCode();
        dto.setId(bo.getId());
        dto.setTaskCode(code);
        dto.setTaskName(ScheduledAdjustLogHelper.withTaskScope(code, bo.getTaskName()));
        dto.setDescription(bo.getDescription());
        dto.setCronExpression(bo.getCronExpression());
        dto.setScheduleEnabled(Integer.valueOf(1).equals(bo.getScheduleEnabled()));
        dto.setParamJson(bo.getParamJson());
        // 已注册实现时提供参数说明和配置页预填值
        RrsScheduledTask task = taskMap.get(code);
        if (task != null) {
            dto.setParamHelp(task.getParamHelp());
            dto.setParamDefaults(task.getParamDefaults());
        }
        dto.setCodeRegistered(task != null);
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
     *
     * 执行顺序：
     * 1. 将任务执行状态、结果、耗时、操作人及过程日志转换为页面展示数据
     *
     * @param bo 待转换的数据库记录
     * @return 定时任务执行历史
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
     *
     * 执行顺序：
     * 1. 检查任务编码不能为空
     *
     * @param taskCode 任务编码
     * @return 代码
     */
    private String trimCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            throw new BizException("任务编码不能为空");
        }
        return taskCode.trim();
    }

    /**
     * 将文本截断到指定最大长度，超出部分丢弃
     *
     * 执行顺序：
     * 1. 文本为空时返回空值
     *
     * @param text 待处理的文本
     * @param max 数据库字段最大长度
     * @return 按指定最大长度截取后的文本，输入为 null 时返回 null
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

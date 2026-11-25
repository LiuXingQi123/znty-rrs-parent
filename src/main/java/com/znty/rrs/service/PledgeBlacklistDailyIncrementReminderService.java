package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.BondReminderDto;
import com.znty.rrs.entity.schedule.ScheduledReminderMessageDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BondReminderMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 质押黑名单库每日增量提醒
 *
 * 执行顺序：
 * 1. 确定扫描池和增量窗口，首次从当天零点开始，后续从上次成功执行开始时间继续
 * 2. 筛选窗口内审批通过的债券和主体调入调出记录，排除已到期债券
 * 3. 通过 {@link ScheduledReminderDeliveryService} 提交候选明细，记录通知状态和本轮处理结果
 */
@Slf4j
@Service
public class PledgeBlacklistDailyIncrementReminderService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "pledge_blacklist_daily_increment_reminder";

    /** 任务名称 */
    public static final String TASK_NAME = "质押黑名单库每日增量提醒";

    /** 解析质押黑名单库提醒的扫描参数 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 未指定扫描范围时使用的质押黑名单库 ID */
    private static final List<Long> DEFAULT_POOL_IDS = Collections.singletonList(17L);

    /** 定时任务页面展示的扫描池、时间窗口和提醒条件 */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[17]}</code>\n"
                    + "poolIds：可选，质押黑名单增量扫描池；未配置时默认 [17]\n"
                    + "增量水位：优先使用本任务上次成功执行开始时间；首次执行从当天 00:00:00 开始\n"
                    + "扫描内容：时间窗口内 audit_status=20 的债券、主体调入调出记录；到期债券不提醒\n"
                    + "消息投递：当前通知通道未接入，仅生成候选明细并写执行过程日志";

    /** 提醒查询 Mapper */
    @Resource
    private BondReminderMapper bondReminderMapper;

    /** 定时任务配置与水位 Mapper */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /** 统一通知投递服务 */
    @Resource
    private ScheduledReminderDeliveryService reminderDeliveryService;

    /**
     * 返回质押黑名单库每日增量提醒任务编码
     *
     * 执行顺序：
     * 1. 返回当前任务定义的固定编码
     *
     * @return 任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 返回质押黑名单库每日增量提醒任务参数说明
     *
     * 执行顺序：
     * 1. 返回当前任务定义的参数说明
     *
     * @return 任务参数说明
     */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行质押黑名单库每日增量提醒
     *
     * 执行顺序：
     * 1. 读取扫描池和上次成功执行水位，构造左闭右开增量窗口
     * 2. 查询窗口内审批通过且仍需提醒的债券、主体调库记录
     * 3. 组装消息、写入可追溯明细并调用统一通知投递服务
     * 4. 返回本轮候选数量、通知状态和过程日志
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        try {
            // 1. 解析本任务扫描池
            List<Long> poolIds = resolvePoolIds();
            // 2. 使用上次成功执行时间构造不重不漏的左闭右开窗口
            Date windowStart = resolveWindowStart(startTime);
            detail.line("扫描条件：ip_adjust_log.is_deleted=0、audit_status=20、target_pool_id IN "
                    + poolIds + "、category_type IN (bond,company)。债券须关联证券主档、状态不为 D，且摘牌日为空或晚于今日");
            // 格式化时间
            detail.line("增量窗口：[" + formatTime(windowStart)
                    // 格式化时间
                    + ", " + formatTime(startTime) + ")");
            // 3. 查询本轮增量提醒候选
            List<BondReminderDto> sourceList = bondReminderMapper.queryPledgeBlacklistIncrementList(
                    poolIds, windowStart, startTime);
            List<BondReminderDto> rows = sourceList == null
                    ? Collections.<BondReminderDto>emptyList() : sourceList;
            detail.line("扫描完成：增量提醒候选 " + rows.size() + " 条");
            // 4. 生成消息及执行历史中的可追溯明细
            ScheduledReminderMessageDto reminder = buildReminder(rows);
            // 将候选明细写入过程日志，通知未接入时仍可人工核对
            appendDetail(rows, detail);
            // 5. 调用统一投递边界；当前实现明确返回未接入
            ScheduledReminderDeliveryService.DeliveryResult delivery = reminderDeliveryService.send(reminder);
            detail.line("消息投递状态=" + (delivery.isDelivered() ? "已发送" : "待接入")
                    + "，说明=" + delivery.getMessage());
            String message = "本轮发现 " + rows.size() + " 条增量提醒记录；" + delivery.getMessage();
            long duration = System.currentTimeMillis() - begin;
            detail.line("任务结束（成功）：候选 " + rows.size() + " 条，通知状态="
                    + (delivery.isDelivered() ? "已发送" : "待接入")
                    + "，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, rows.size(), startTime,
                    duration, detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{}执行异常", TASK_NAME, e);
            detail.line("ERROR", "执行异常：" + e.getMessage());
            detail.line("ERROR", "任务结束（失败）：提醒扫描或组装过程异常，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, "执行异常: " + e.getMessage(),
                    startTime, duration, detail.build());
        }
    }

    /**
     * 读取并解析质押黑名单扫描池
     *
     * 未配置扩展参数时使用默认池 17，同一池只保留一组扫描配置
     *
     * 执行顺序：
     * 1. 按任务编码查询任务配置
     * 2. 检查poolIds 须为数字数组
     * 3. 检查poolIds 不能为空数组
     *
     * @return 去重后的扫描池 ID
     */
    private List<Long> resolvePoolIds() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf == null || !StringUtils.hasText(conf.getParamJson())) {
            return DEFAULT_POOL_IDS;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(conf.getParamJson());
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"poolIds\":[17]}");
        }
        JsonNode node = root.get("poolIds");
        if (node == null || node.isNull()) {
            return DEFAULT_POOL_IDS;
        }
        if (!node.isArray()) {
            throw new BizException("poolIds 须为数字数组");
        }
        Set<Long> ids = new LinkedHashSet<Long>();
        for (JsonNode item : node) {
            if (item == null || !item.isNumber() || item.asLong() <= 0) {
                throw new BizException("poolIds 元素须为正整数，非法值: " + item);
            }
            ids.add(item.asLong());
        }
        if (ids.isEmpty()) {
            throw new BizException("poolIds 不能为空数组");
        }
        return new ArrayList<Long>(ids);
    }

    /**
     * 解析上次成功执行水位
     *
     * 有成功水位时从该时间开始，首次执行从当天零点开始
     *
     * 执行顺序：
     * 1. 有成功执行历史时沿用最近一次开始时间
     * 2. 首次运行时用本次执行当天零点作为扫描起点
     *
     * @param currentStart 本轮任务开始时间
     * @return 增量窗口起始时间
     */
    private Date resolveWindowStart(Date currentStart) {
        Date previous = scheduledTaskMapper.queryLastSuccessStartTime(TASK_CODE);
        if (previous != null) {
            return previous;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentStart);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 构建后续通知通道可直接消费的消息
     *
     * 执行顺序：
     * 1. 将任务编码、标题及扫描明细写入提醒
     * 2. 按是否有增量决定无变更说明或记录条数
     *
     * @param rows 行列表
     * @return 提醒
     */
    private ScheduledReminderMessageDto buildReminder(List<BondReminderDto> rows) {
        ScheduledReminderMessageDto message = new ScheduledReminderMessageDto();
        message.setTaskCode(TASK_CODE);
        message.setTitle(TASK_NAME);
        message.setItems(rows);
        message.setContent(rows.isEmpty() ? "当日质押黑名单库无增量调整"
                : "当日质押黑名单库新增调库提醒 " + rows.size() + " 条");
        return message;
    }

    /**
     * 将候选明细写入过程日志，通知未接入时仍可人工核对
     *
     * 执行顺序：
     * 1. 逐项格式化时间
     *
     * @param rows 行列表
     * @param detail 过程日志
     */
    private void appendDetail(List<BondReminderDto> rows, TaskDetailLog detail) {
        if (rows.isEmpty()) {
            detail.line("本轮无增量调库记录");
            return;
        }
        for (BondReminderDto row : rows) {
            // 1. 将可空字段和审核时间格式化为可读文本
            detail.line("提醒候选：池=" + row.getTargetPoolName()
                    + "，证券=" + row.getSecurityShortName() + "(" + row.getSecurityCode() + ")"
                    // 将空字符串字段安全输出到过程日志
                    + "，主体=" + nullToEmpty(row.getIssuerName())
                    // 将空字符串字段安全输出到过程日志
                    + "，方向=" + nullToEmpty(row.getAdjustMode())
                    // 格式化时间
                    + "，审核时间=" + formatTime(row.getAuditTime()));
        }
    }

    /**
     * 格式化时间
     *
     * 执行顺序：
     * 1. 输入为 null 时返回空字符串，否则按 yyyy-MM-dd HH:mm:ss 输出
     *
     * @param value 入池时间
     * @return yyyy-MM-dd HH:mm:ss 格式的入池时间，输入为 null 时返回空字符串
     */
    private String formatTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /**
     * 将缺失文本转换成日志可用的空字符串
     *
     * 执行顺序：
     * 1. 输入为 null 时返回空字符串，有值时原样返回
     *
     * @param value 待统一空值的文本
     * @return 有值时返回原文本，空值时返回空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

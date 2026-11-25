package com.znty.rrs.service;

import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 为定时调库记录补充任务名称和调整原因
 *
 * 本类是定时调库的日志辅助类，按任务编码补充仅主体或仅债券后缀，运行期间通过当前线程将任务名称传给共享业务，任务结束时恢复之前的名称
 * 生成发行主体、触发主体、投资池和日期等判断说明，供自动调库原因使用
 */
final class ScheduledAdjustLogHelper {

    /** 当前调度线程执行的任务名称，供共享业务方法记录调库来源 */
    private static final ThreadLocal<String> TASK_NAME = new ThreadLocal<>();

    /**
     * 限制定时调库日志工具类的实例化
     */
    private ScheduledAdjustLogHelper() {
    }

    /**
     * 按任务处理对象补齐范围后缀
     *
     * 执行顺序：
     * 1. 按任务编码找出适用的主体或债券范围后缀
     * 2. 去掉名称首尾空白，仅在缺少该后缀时追加
     *
     * @param taskCode 定时任务编码
     * @param name 用于生成调库日志的任务名称
     * @return 适用时补齐处理范围后缀的任务名称
     */
    static String withTaskScope(String taskCode, String name) {
        if (taskCode == null || !StringUtils.hasText(name)) {
            return name;
        }
        String suffix;
        switch (taskCode) {
            case CompanyObserverPoolSyncService.TASK_CODE:
            case CompanyOuterRatingAaMinusAutoInService.TASK_CODE:
            case CompanyOuterRatingNotAaMinusAutoOutService.TASK_CODE:
                suffix = "（仅主体）";
                break;
            case CompanyNewBondAutoInService.TASK_CODE:
            case CompanyNotInPoolBondAutoOutService.TASK_CODE:
            case CompanyAbsBondAutoInService.TASK_CODE:
            case BondTempCodeReplaceService.TASK_CODE:
            case BondSecurityTypeChangeService.TASK_CODE:
            case WindCodeSyncService.TASK_CODE:
            case GradeRuleAlertService.TASK_CODE:
            case BondIssuerNotInCompanyPoolReminderService.TASK_CODE:
                suffix = "（仅债券）";
                break;
            default:
                return name;
        }
        String taskName = name.trim();
        return taskName.endsWith(suffix) ? taskName : taskName + suffix;
    }

    /**
     * 执行任务并在退出时恢复当前线程原有的任务名称
     *
     * 执行顺序：
     * 1. 把任务名称写入当前线程后执行任务
     * 2. 任务退出时恢复原名称，原来没有名称时清除线程记录
     *
     * @param <T> 任务返回值的类型
     * @param taskName 本次任务执行使用的名称
     * @param task 要执行的任务
     * @return 任务执行结果
     */
    static <T> T runWithTaskName(String taskName, Supplier<T> task) {
        String previousName = TASK_NAME.get();
        TASK_NAME.set(taskName);
        try {
            return task.get();
        } finally {
            if (previousName == null) {
                TASK_NAME.remove();
            } else {
                TASK_NAME.set(previousName);
            }
        }
    }

    /**
     * 使用定时任务管理页的名称，直接执行时使用业务默认名称
     *
     * 执行顺序：
     * 1. 读取线程中的任务名称，无名称时采用传入的默认名称
     * 2. 按日志格式拼接任务名称标签
     *
     * @param defaultName 直接执行时使用的默认任务名称
     * @return 带展示格式的任务名称标签
     */
    static String taskLabel(String defaultName) {
        String name = TASK_NAME.get();
        return "【任务名称：" + (name == null ? defaultName : name.trim()) + "】";
    }

    /**
     * 共享业务在定时任务执行期间为原因加上任务名称前缀
     *
     * 执行顺序：
     * 1. 当前线程有任务名称时给原因加上名称标签，否则原样返回原因
     *
     * @param reason 原始调库原因
     * @return 当前任务执行期间带名称前缀的原因，其他时候返回原原因
     */
    static String labelCurrentTask(String reason) {
        return TASK_NAME.get() == null ? reason : taskLabel(null) + reason;
    }

    /**
     * 在基础原因后以中文括号追加非空判断信息
     *
     * 执行顺序：
     * 1. 去掉基础原因和各项判断信息首尾的空白，过滤空判断信息
     * 2. 将有效判断信息用中文括号包起来并追加到基础原因
     *
     * @param reason 原始调库原因
     * @param details 需要追加的判断信息
     * @return 拼接后的原因，基础原因和判断信息都为空时为 {@code null}
     */
    static String appendDetails(String reason, String... details) {
        // 基础原因只有空白时只保留有效判断信息
        String baseReason = trimToNull(reason);
        List<String> validDetails = new ArrayList<>();
        if (details != null) {
            for (String detail : details) {
                if (StringUtils.hasText(detail)) {
                    validDetails.add(detail.trim());
                }
            }
        }
        if (validDetails.isEmpty()) {
            return baseReason;
        }
        String detailText = "（" + String.join("；", validDetails) + "）";
        return baseReason == null ? detailText : baseReason + detailText;
    }

    /**
     * 生成发行主体判断信息，名称与代码同时存在时均展示
     *
     * 执行顺序：
     * 1. 从候选记录取发行主体名称和代码，去掉首尾空白
     * 2. 资料齐全时同时展示名称和代码，只存在一项时展示该项
     *
     * @param item 本次定时调库的证券候选记录
     * @return 发行主体说明，名称和代码都为空时为 {@code null}
     */
    static String issuerDetail(ScheduledAdjustCandidateDto item) {
        if (item == null) {
            return null;
        }
        // 1. 读取发行主体名称和代码，并清理首尾空白
        String issuerName = trimToNull(item.getIssuerName());
        // 清理发行主体代码，代码为空时可只展示名称
        String issuerCode = trimToNull(item.getIssuerCode());
        // 2. 按可用字段拼接发行主体说明
        if (issuerName != null && issuerCode != null) {
            return "发行主体：" + issuerName + "/" + issuerCode;
        }
        String issuer = issuerName != null ? issuerName : issuerCode;
        return issuer == null ? null : "发行主体：" + issuer;
    }

    /**
     * 记录 ABS 自动联动入池时尚未确认主体的待确认状态
     *
     * 执行顺序：
     * 1. 确认候选属于 ABS 且尚无发行主体代码时，返回主体身份待确认的提示
     *
     * @param item 本次定时调库的证券候选记录
     * @return ABS 发行主体待确认提示，不适用时为 {@code null}
     */
    static String passiveAbsIdentityDetail(ScheduledAdjustCandidateDto item) {
        if (item == null || (!("abs".equals(item.getSecurityType())
                || Integer.valueOf(1).equals(item.getAbsFlag())))
                || StringUtils.hasText(item.getIssuerCode())) {
            return null;
        }
        return "ABS主体身份：尚未确认";
    }

    /**
     * 记录自动联动的触发主体代码
     *
     * 执行顺序：
     * 1. 候选中有触发主体代码时，去掉首尾空白并拼接展示标签
     *
     * @param item 本次定时调库的证券候选记录
     * @return 联动触发主体说明，没有触发主体代码时为 {@code null}
     */
    static String triggerCompanyDetail(ScheduledAdjustCandidateDto item) {
        if (item == null || !StringUtils.hasText(item.getTriggerCompanyCode())) {
            return null;
        }
        return "联动触发主体：" + item.getTriggerCompanyCode().trim();
    }

    /**
     * 生成主体联动旗下 ABS 的原因片段
     *
     * 仅在联动对象为 ABS 且主体身份尚未确认时生效，此时需同时记录触发主体，
     * 供研究员回溯该笔联动入池的依据；主体已确认时返回 {@code null}，由
     * {@link #appendDetails(String, String...)} 自动跳过
     *
     * 执行顺序：
     * 1. 判断证券是否为发行主体未确认的 ABS
     * 2. 生成主体身份待确认的提示，有触发主体代码时一并附上
     *
     * @param security 被联动的证券主数据
     * @param triggerCompanyCode 触发本次联动的主体代码
     * @return 可直接追加的原因片段，无需追加时返回 {@code null}
     */
    static String absCompanySyncDetail(SecurityInfoBo security, String triggerCompanyCode) {
        if (!CreditBondSpecialInboundRule.isAbs(security)
                || StringUtils.hasText(security.getIssuerCode())) {
            return null;
        }
        // 触发主体代码为空时只提示 ABS 主体待确认
        String triggerCode = trimToNull(triggerCompanyCode);
        return triggerCode == null ? "ABS主体身份：尚未确认" : "ABS主体身份：尚未确认；联动触发主体：" + triggerCode;
    }

    /**
     * 生成池判断信息，优先展示池名称，无名称时回退到池 ID
     *
     * 执行顺序：
     * 1. 先用池名称生成信息，名称为空时改用池 ID，两项都缺失时返回空值
     *
     * @param label 用于展示的字段名称
     * @param pool 投资池配置
     * @param poolId 找不到池名称时供展示的投资池 ID
     * @return 池名称或池 ID 的说明，两项都为空时为 {@code null}
     */
    static String poolDetail(String label, InvestmentPoolBo pool, Long poolId) {
        // 去掉池名称首尾空白，名称为空时用池 ID
        String poolName = pool == null ? null : trimToNull(pool.getPoolName());
        if (poolName != null) {
            return label + "：" + poolName;
        }
        return poolId == null ? null : label + "：" + poolId;
    }

    /**
     * 将主数据 yyyyMMdd 日期转换为界面易读格式
     *
     * 执行顺序：
     * 1. 排除空白日期，将 8 位数字日期转成 yyyy-MM-dd
     * 2. 在日期前加上展示标签
     *
     * @param label 用于展示的字段名称
     * @param date 需要展示的日期原值
     * @return 带展示标签的日期文本，日期为空时为 {@code null}
     */
    static String dateDetail(String label, String date) {
        // 日期为空白时省略整段日期信息
        String value = trimToNull(date);
        if (value == null) {
            return null;
        }
        if (value.matches("\\d{8}")) {
            value = value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
        }
        return label + "：" + value;
    }

    /**
     * 去除字符串首尾空白，并将空字符串转换为 {@code null}
     *
     * 执行顺序：
     * 1. 有非空白内容时返回去掉首尾空白的文本，否则返回空值
     *
     * @param value 待去除首尾空白的文本
     * @return 去掉首尾空白的文本，没有有效内容时为 {@code null}
     */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

package com.znty.rrs.schedule;

/**
 * 平台可调度定时任务契约。
 *
 * <p><b>运行时以库表为准、代码只做两件事：</b>
 * <ol>
 *   <li>提供稳定 {@link #getTaskCode()} 与业务 {@link #execute()}（必须写在代码里）</li>
 *   <li>提供名称/说明/cron/启停/扩展参数的<b>默认值</b>，仅在库表尚无该任务时种子写入</li>
 * </ol>
 *
 * <p>页面改过的名称、说明、cron、启停、扩展参数均落在 {@code sys_scheduled_task}，
 * 列表展示与调度挂载优先读库，不再依赖代码常量。
 */
public interface RrsScheduledTask {

    /**
     * 任务唯一编码（稳定标识，如 company_new_bond_auto_in）。
     * 与库表 task_code、调度注册键绑定，不可页面修改。
     *
     * @return 任务编码
     */
    String getTaskCode();

    /**
     * 默认任务名称（仅种子写入；运行展示读库）。
     *
     * @return 任务名称
     */
    String getTaskName();

    /**
     * 默认任务说明（仅种子写入；运行展示读库）。
     *
     * @return 说明文案
     */
    String getDescription();

    /**
     * 默认 cron（仅种子写入；调度读库）。
     *
     * @return cron 表达式
     */
    String getDefaultCronExpression();

    /**
     * 默认是否启用定时调度（仅种子写入；调度读库）。
     *
     * @return true=默认启用
     */
    boolean isDefaultScheduleEnabled();

    /**
     * 默认扩展参数（仅种子写入；业务执行读库，可 null）。
     *
     * @return 扩展参数字符串
     */
    String getDefaultParamJson();

    /**
     * 执行任务业务逻辑（手动与定时共用）。
     * 业务规则写在代码中；可变配置（如池映射）应从库表 param_json 读取。
     *
     * @return 执行结果摘要
     */
    ScheduledTaskResult execute();
}

package com.znty.rrs.schedule;

import java.util.Map;

/**
 * 定义定时任务的编码、执行入口和扩展参数约定
 *
 * 不同任务各自按 Cron 触发，有池状态依赖时需要错开执行时间，并留足前序任务的运行时间
 * 扩展参数以 JSON 对象保存在 {@code sys_scheduled_task.param_json}，由各任务自行读取和解析
 *
 * 新增任务步骤：
 * 1. 按需求 29 第 4.1 和 4.2 节评估任务依赖，确定 Cron，并同步演示配置和需求中的执行顺序
 * 2. 在定时任务管理页新增配置，记录任务编码
 * 3. 实现 {@link #getTaskCode()} 和 {@link #execute()}，分别提供同一任务编码和业务执行入口
 * 4. 按需通过 {@link #getParamHelp()} 提供填写说明，通过 {@link #getParamDefaults()} 提供页面预填值
 * 5. 部署后在页面确认任务编码已绑定实现，再执行任务
 */
public interface RrsScheduledTask {

    /** 悬浮提示专用说明行的标记前缀，前端展示时移除此标记 */
    String PARAM_HELP_TOOLTIP_PREFIX = "[[TOOLTIP]]";

    /**
     * 提供业务实现与定时任务配置绑定时使用的编码
     *
     * @return 与 {@code sys_scheduled_task.task_code} 一致的任务编码
     */
    String getTaskCode();

    /**
     * 执行一次定时任务业务
     *
     * 任务需要扩展参数时，由实现类按该任务约定读取和解析
     *
     * @return 本次执行结果，包含是否成功、结果说明、影响条数和执行时间，可附过程日志
     */
    ScheduledTaskResult execute();

    /**
     * 提供配置页展示的扩展参数填写说明
     *
     * 参数格式由各任务定义，无需填写参数时可提示「请留空」
     * 仅在悬浮提示中展示的示例或字段说明，以 {@link #PARAM_HELP_TOOLTIP_PREFIX} 标记
     *
     * 执行顺序：
     * 1. 任务未提供专用说明时，默认返回 null
     *
     * @return 配置页展示的说明文案，没有说明时返回 null
     */
    default String getParamHelp() {
        return null;
    }

    /**
     * 提供配置页可预填的扩展参数默认值
     *
     * 配置页用默认值补齐缺失的字段，保留已保存的值
     *
     * 执行顺序：
     * 1. 任务未提供专用默认值时，默认返回 null
     *
     * @return 可预填的字段名及对应值，没有默认值时返回 null
     */
    default Map<String, Object> getParamDefaults() {
        return null;
    }
}

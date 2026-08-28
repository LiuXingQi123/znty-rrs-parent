package com.znty.rrs.schedule;

/**
 * 定时任务业务实现接口（扩展点）
 * <p>
 * 新增任务步骤：① 先评估与已有任务的执行顺序（见需求 29 第 4.1 / 4.2 节），再定 cron；
 * ② 页面「新增」一条配置并记下 task_code；
 * ③ 新建类 implements 本接口，getTaskCode() 返回同一编码并实现 execute()；
 * ④ 部署后即可执行。扩展参数存于库表 param_json，由本任务自行解析；
 * <strong>仅支持 JSON 对象</strong>，并通过 {@link #getParamHelp()} 向配置页提供填写说明。
 * 不同 taskCode 之间无编排，有池状态依赖时必须靠 cron 错开先后。
 * </p>
 */
public interface RrsScheduledTask {

    /**
     * 参数说明中仅在配置页悬浮提示展示的行前缀。
     * 前缀由前端移除后展示，避免根据中文文案猜测行的展示位置。
     */
    String PARAM_HELP_TOOLTIP_PREFIX = "[[TOOLTIP]]";

    /**
     * 返回任务编码，须与库表 sys_scheduled_task.task_code 一致
     */
    String getTaskCode();

    /**
     * 执行本任务业务逻辑，返回成功/失败及影响条数等结果
     */
    ScheduledTaskResult execute();

    /**
     * 扩展参数填写说明（配置页展示）
     * <p>
     * 通用约定：param_json 仅 JSON 对象，由本任务解析所需字段；无参数可说明「请留空」。
     * 勿在前端写死某一任务的格式。仅需在悬浮提示中展示的示例、字段说明等行，
     * 应以前缀 {@link #PARAM_HELP_TOOLTIP_PREFIX} 标记。
     * </p>
     *
     * @return 说明文案，无则 null
     */
    default String getParamHelp() {
        return null;
    }
}

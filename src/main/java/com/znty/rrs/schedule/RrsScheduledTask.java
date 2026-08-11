package com.znty.rrs.schedule;

/**
 * 定时任务业务实现接口（扩展点）
 * <p>
 * 新增任务步骤：① 页面「新增」一条配置并记下 task_code；
 * ② 新建类 implements 本接口，getTaskCode() 返回同一编码并实现 execute()；
 * ③ 部署后即可执行。扩展参数存于库表 param_json，由本任务自行解析；
 * <strong>仅支持 JSON 对象</strong>，并通过 {@link #getParamHelp()} 向配置页提供填写说明。
 * </p>
 */
public interface RrsScheduledTask {

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
     * 勿在前端写死某一任务的格式。
     * </p>
     *
     * @return 说明文案，无则 null
     */
    default String getParamHelp() {
        return null;
    }
}

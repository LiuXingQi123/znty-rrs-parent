package com.znty.rrs.service;

import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 债券临时代码替换
 *
 * 执行顺序：
 * 1. 扫描已补齐正式证券代码的临时代码记录
 * 2. 复用人工转正式业务分叉处理每条记录
 * 3. 记录成功和失败明细，失败记录保留 temporary 状态供后续重试
 * 4. 汇总本轮候选、成功和失败数量
 */
@Slf4j
@Service
public class BondTempCodeReplaceService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "bond_temp_code_replace";

    /** 任务名称 */
    public static final String TASK_NAME = "债券临时代码替换";

    private static final String PARAM_HELP =
            "参数说明：本任务无需扩展参数，请将 param_json 留空\n"
                    + "扫描条件：rrs_temp_security_code.status=temporary，且 security_code 已由外部数据补齐\n"
                    + "正式数据：按 security_code 查询 rrs_securityinfo，名称、市场、类型均以正式主数据为准\n"
                    + "处理规则：普通池及CRMW标的按同券全部市场代码整组转换；流程中沿用原审批，已在池临时码退出后正式组统一进入\n"
                    + "业务校验：信用债大库独立在途、市场与池关系；不重查容量、池启停和业务锁定\n"
                    + "转换保障：主数据有效、相关池整组状态一致、写入成功；任一失败回滚本条映射\n"
                    + "CRMW范围：同凭证下逐标的市场代码处理，凭证自身不扩展；即时调库记录调整人为系统，来源为 oprt_source=job\n"
                    + "任务边界：本任务独立于 wind_code_sync，分别扫描登记映射和 Wind 事件，共用业务转换组件";

    /** 临时代码 Mapper */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** 临时代码业务服务 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    /** 返回债券临时代码替换任务编码 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回债券临时代码替换任务参数说明 */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行债券临时代码替换
     *
     * 执行顺序：
     * 1. 查询满足条件的临时代码候选
     * 2. 逐条复用人工转正式业务处理
     * 3. 记录每条成功或失败结果
     * 4. 汇总本轮处理数量并返回任务结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        // 1. 记录任务开始和扫描条件
        detail.line("任务开始：【" + TASK_NAME + "】");
        detail.line("扫描条件：rrs_temp_security_code.is_deleted=0、status=temporary、"
                + "security_code IS NOT NULL 且 TRIM 后非空");
        try {
            // 2. 查询待替换临时代码
            List<TempSecurityCodeBo> sourceList = tempSecurityCodeMapper.queryJobReadyTempSecurityCodeList();
            List<TempSecurityCodeBo> rows = sourceList == null
                    ? Collections.<TempSecurityCodeBo>emptyList() : sourceList;
            detail.line("扫描完成：待替换候选 " + rows.size() + " 条");
            int successCount = 0;
            int failureCount = 0;
            // 3. 逐条复用人工转正式业务处理
            for (TempSecurityCodeBo row : rows) {
                try {
                    // 3.1 复用人工转正式的完整业务分叉，并将操作来源记为 job
                    tempSecurityCodeService.editTempSecurityCodeToUpdatedByJob(row.getId());
                    successCount++;
                    detail.line("替换成功：记录ID=" + row.getId()
                            + "，临时代码=" + row.getTempSecurityCode()
                            + "，正式代码=" + row.getSecurityCode());
                } catch (Exception e) {
                    failureCount++;
                    log.error("临时代码定时替换失败，id={}", row.getId(), e);
                    detail.line("ERROR", "替换失败：记录ID=" + row.getId()
                            + "，临时代码=" + row.getTempSecurityCode()
                            + "，正式代码=" + row.getSecurityCode()
                            + "，原因=" + e.getMessage());
                }
            }
            // 4. 汇总结果并返回任务状态
            long duration = System.currentTimeMillis() - begin;
            if (failureCount > 0) {
                String message = "扫描 " + rows.size() + " 条，成功 " + successCount
                        + " 条，失败 " + failureCount + " 条";
                detail.line("ERROR", "任务结束（失败）：候选 " + rows.size()
                        + " 条，成功 " + successCount + " 条，失败 " + failureCount
                        + " 条，耗时 " + duration + " 毫秒");
                return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, message,
                        startTime, duration, detail.build());
            }
            String message = "本轮共替换 " + successCount + " 条债券临时代码";
            detail.line("任务结束（成功）：候选 " + rows.size() + " 条，成功 "
                    + successCount + " 条，失败 0 条，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, successCount,
                    startTime, duration, detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{}执行异常", TASK_NAME, e);
            detail.line("ERROR", "执行异常：" + e.getMessage());
            detail.line("ERROR", "任务结束（失败）：扫描或处理过程异常，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, "执行异常: " + e.getMessage(),
                    startTime, duration, detail.build());
        }
    }
}

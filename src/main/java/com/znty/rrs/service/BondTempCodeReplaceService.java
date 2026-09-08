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
 * 债券临时代码替换任务：扫描已补齐正式码映射的临时代码，复用人工转正式业务分叉。
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
                    + "处理规则：在途日志只改码；已在池执行临时码出池，并在正式码未在同池时调入\n"
                    + "CRMW范围：同时处理证券字段和 CRMW 字段引用，来源记录为 oprt_source=job\n"
                    + "任务边界：本任务独立于 wind_code_sync，不调用也不修改该空壳任务";

    /** 临时代码 Mapper */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** 临时代码业务服务 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行临时代码自动替换；单条记录独立事务，失败项留在 temporary 状态供后续重试。
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        detail.line("扫描条件：rrs_temp_security_code.is_deleted=0、status=temporary、"
                + "security_code IS NOT NULL 且 TRIM 后非空");
        try {
            List<TempSecurityCodeBo> sourceList = tempSecurityCodeMapper.queryJobReadyTempSecurityCodeList();
            List<TempSecurityCodeBo> rows = sourceList == null
                    ? Collections.<TempSecurityCodeBo>emptyList() : sourceList;
            detail.line("扫描完成：待替换候选 " + rows.size() + " 条");
            int successCount = 0;
            int failureCount = 0;
            for (TempSecurityCodeBo row : rows) {
                try {
                    // 复用人工转正式的完整业务分叉，并将操作来源记为 job
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

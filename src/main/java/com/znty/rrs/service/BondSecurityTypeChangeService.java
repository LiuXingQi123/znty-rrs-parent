package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.BondSecurityTypeChangeDto;
import com.znty.rrs.mapper.BondSecurityMaintenanceMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 债券类型变更任务：以证券主数据为准同步当前生效池状态及其关联调库日志。
 */
@Slf4j
@Service
public class BondSecurityTypeChangeService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "bond_security_type_change";

    /** 任务名称 */
    public static final String TASK_NAME = "债券类型变更";

    private static final String PARAM_HELP =
            "参数说明：本任务无需扩展参数，请将 param_json 留空\n"
                    + "数据来源：rrs_securityinfo.security_type 为证券主数据最新类型\n"
                    + "处理范围：仅同步新旧类型都属于 dict_security_type.category_type=bond 的生效池状态\n"
                    + "写入范围：ip_pool_status、ip_pool_status_crmw 及各池状态当前关联的 ip_adjust_log\n"
                    + "历史口径：仅改当前池状态关联日志，其他历史调库日志保留原始快照";

    /** 债券主数据维护 Mapper */
    @Resource
    private BondSecurityMaintenanceMapper bondSecurityMaintenanceMapper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行债券类型同步。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        detail.line("扫描条件：ip_pool_status/ip_pool_status_crmw.is_deleted=0、audit_status=20；"
                + "rrs_securityinfo 按 wind_code 匹配；新旧 security_type 均属于 bond 大类且不相等");
        try {
            List<BondSecurityTypeChangeDto> normalRows =
                    bondSecurityMaintenanceMapper.queryPoolSecurityTypeChangeList();
            List<BondSecurityTypeChangeDto> crmwRows =
                    bondSecurityMaintenanceMapper.queryCrmwPoolSecurityTypeChangeList();
            int normalCandidateCount = normalRows == null ? 0 : normalRows.size();
            int crmwCandidateCount = crmwRows == null ? 0 : crmwRows.size();
            detail.line("扫描完成：普通池候选 " + normalCandidateCount
                    + " 条，CRMW 池候选 " + crmwCandidateCount + " 条");
            // 同步普通池证券类型
            int normalCount = editSecurityTypeList(normalRows, false, detail);
            // 同步 CRMW 池标的证券类型
            int crmwCount = editSecurityTypeList(crmwRows, true, detail);
            int total = normalCount + crmwCount;
            int skippedCount = normalCandidateCount + crmwCandidateCount - total;
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共同步 " + total + " 条债券池状态类型";
            detail.line("任务结束（成功）：普通池更新 " + normalCount
                    + " 条，CRMW 池更新 " + crmwCount + " 条，跳过 " + skippedCount
                    + " 条，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, total, startTime,
                    duration, detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{}执行异常", TASK_NAME, e);
            detail.line("ERROR", "执行异常：" + e.getMessage());
            detail.line("ERROR", "任务结束（失败）：类型同步过程异常，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, "执行异常: " + e.getMessage(),
                    startTime, duration, detail.build());
        }
    }

    /**
     * 同步一类池状态列表。
     *
     * @param sourceList 待同步记录
     * @param crmwPool 是否为 CRMW 池表
     * @param detail 过程日志
     * @return 实际更新池状态条数
     */
    private int editSecurityTypeList(List<BondSecurityTypeChangeDto> sourceList,
                                     boolean crmwPool,
                                     TaskDetailLog detail) {
        List<BondSecurityTypeChangeDto> rows = sourceList == null
                ? Collections.<BondSecurityTypeChangeDto>emptyList() : sourceList;
        int count = 0;
        Date updateTime = new Date();
        for (BondSecurityTypeChangeDto row : rows) {
            // 先条件更新池状态，避免覆盖扫描后的并发变更
            int updated = crmwPool
                    ? bondSecurityMaintenanceMapper.editCrmwPoolSecurityType(row, updateTime)
                    : bondSecurityMaintenanceMapper.editPoolSecurityType(row, updateTime);
            if (updated != 1) {
                detail.line("WARN", "池状态已变化，跳过：表="
                        + (crmwPool ? "ip_pool_status_crmw" : "ip_pool_status")
                        + "，poolStatusId=" + row.getPoolStatusId()
                        + "，证券=" + row.getSecurityCode()
                        + "，待变更类型=" + row.getOldSecurityType() + " → " + row.getNewSecurityType());
                continue;
            }
            if (row.getAdjustLogId() != null) {
                // 同步当前池状态关联的调库日志类型
                int logUpdated = bondSecurityMaintenanceMapper.editAdjustLogSecurityType(
                        row.getAdjustLogId(), row.getNewSecurityType(), updateTime);
                if (logUpdated == 0) {
                    detail.line("WARN", "当前关联调库日志不存在或已删除，adjustLogId=" + row.getAdjustLogId());
                }
            } else {
                detail.line("WARN", "池状态缺少关联调库日志，poolStatusId=" + row.getPoolStatusId());
            }
            count++;
            detail.line("同步成功：表=" + (crmwPool ? "ip_pool_status_crmw" : "ip_pool_status")
                    + "，poolStatusId=" + row.getPoolStatusId()
                    + "，adjustLogId=" + row.getAdjustLogId()
                    + "，证券=" + row.getSecurityCode()
                    + "，类型=" + row.getOldSecurityType() + " → " + row.getNewSecurityType());
        }
        return count;
    }
}

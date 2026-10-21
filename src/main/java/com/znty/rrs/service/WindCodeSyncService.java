package com.znty.rrs.service;

import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Wind 代码变更同步：按唯一证券 ID 逐条事务复用临时代码业务转换。 */
@Slf4j
@Service
public class WindCodeSyncService implements RrsScheduledTask {
    /** 任务编码。 */
    public static final String TASK_CODE = "wind_code_sync";
    /** 任务名称。 */
    public static final String TASK_NAME = "Wind代码变更同步";
    /** ODS 变更事件读取组件。 */
    @Resource
    private WindCodeSyncMapper windCodeSyncMapper;
    /** 主档更名及共享转换事务入口。 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    /** 返回任务编码。 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回页面参数说明及池状态依赖。 */
    @Override
    public String getParamHelp() {
        return "参数说明：本任务无需扩展参数，请将 param_json 留空\n"
                + "扫描条件：ais_inv_ods.wind_cbondchangewindcode，CHANGE_DATE 为昨天至今天的 yyyyMMdd，旧码不等于新码\n"
                + "身份匹配：按唯一 SEC_ID 查询 rrs_securityinfo，原地更名并保留证券 ID\n"
                + "登记处理：official 补登记；temporary 复用原登记并转为 temp_converted；temp_converted 新增本次登记保留历史\n"
                + "处理范围：仅本次旧新码，复用普通池、CRMW及在途申请转换；主档缺失特别提示并跳过\n"
                + "执行保障：单条事务失败回滚，完成登记保存事件标记，重复扫描不重复调库\n"
                + "执行频率：Demo 每 10 分钟的第 2 分钟执行，会修改池状态，须与其他调库任务错开";
    }

    /** 逐条汇总成功、主档缺失、重复及失败，一条错误不阻断其他证券。 */
    @Override
    public ScheduledTaskResult execute() {
        Date start = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String from = today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String to = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        detail.line("任务开始：【" + TASK_NAME + "】");
        detail.line("扫描条件：ais_inv_ods.wind_cbondchangewindcode，CHANGE_DATE=" + from + "～" + to
                + "（yyyyMMdd），S_INFO_OLDWINDCODE!=S_INFO_NEWWINDCODE；按唯一 SEC_ID 匹配主档");
        int updated = 0;
        int missing = 0;
        int repeated = 0;
        int failed = 0;
        try {
            List<WindCodeChangeDto> rows = windCodeSyncMapper.queryRecentWindCodeChangeList(from, to);
            detail.line("扫描完成：候选 " + rows.size() + " 条");
            // 按证券分组后依据旧新码链处理，不依赖同日 OPDATE 的完整性。
            Map<String, List<WindCodeChangeDto>> groups = groupChanges(rows);
            for (List<WindCodeChangeDto> group : groups.values()) {
                List<WindCodeChangeDto> remaining = new ArrayList<>(group);
                while (!remaining.isEmpty()) {
                    // 先处理没有尚待处理前驱的事件。
                    WindCodeChangeDto row = nextChange(remaining);
                    if (row == null) {
                        failed += remaining.size();
                        detail.line("ERROR", "代码变更链存在循环，SEC_ID=" + remaining.get(0).getSecId());
                        break;
                    }
                    remaining.remove(row);
                    String identity = "SEC_ID=" + row.getSecId() + "，旧代码=" + row.getOldWindCode()
                            + "，新代码=" + row.getNewWindCode() + "，变更日期=" + row.getChangeDate();
                    try {
                        WindCodeChangeResult result = tempSecurityCodeService.editWindCodeChangeByJob(row);
                        if (result == WindCodeChangeResult.UPDATED) {
                            updated++;
                            detail.line("换码成功：" + identity);
                        } else if (result == WindCodeChangeResult.MISSING_MASTER) {
                            missing++;
                            detail.line("WARN", "特别提示：按 SEC_ID 未找到证券主档，本条不处理，请核查主档数据及ID映射；" + identity);
                        } else if (result == WindCodeChangeResult.ALREADY_COMPLETED) {
                            repeated++;
                            detail.line("重复事件跳过：" + identity);
                        } else {
                            throw new IllegalStateException("Wind 单条事务未返回明确结果");
                        }
                    } catch (Exception e) {
                        failed++;
                        log.error("Wind 换码失败，SEC_ID={}，旧码={}，新码={}", row.getSecId(), row.getOldWindCode(), row.getNewWindCode(), e);
                        detail.line("ERROR", "换码失败：" + identity + "，原因=" + e.getMessage());
                        failed += remaining.size();
                        if (!remaining.isEmpty()) {
                            detail.line("ERROR", "同一证券后续 " + remaining.size() + " 条事件未执行：前置换码失败，SEC_ID=" + row.getSecId());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            failed++;
            log.error("Wind 代码变更扫描异常", e);
            detail.line("ERROR", "扫描执行异常：" + e.getMessage());
        }
        long duration = System.currentTimeMillis() - begin;
        String message = "成功 " + updated + " 条，主档缺失跳过 " + missing + " 条，重复跳过 " + repeated + " 条，失败 " + failed + " 条";
        detail.line("任务结束（" + (failed == 0 ? "成功" : "失败") + "）：" + message + "，耗时 " + duration + " 毫秒");
        return failed == 0 ? ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, updated, start, duration, detail.build())
                : ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, message, start, duration, detail.build());
    }

    /** 按唯一证券 ID 分组，保持不同证券的源查询顺序。 */
    private Map<String, List<WindCodeChangeDto>> groupChanges(List<WindCodeChangeDto> rows) {
        Map<String, List<WindCodeChangeDto>> groups = new LinkedHashMap<>();
        for (WindCodeChangeDto row : rows) {
            groups.computeIfAbsent(row.getSecId(), key -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    /** 选择无剩余前驱的事件；同一事件的重复行由事务完成标记去重。 */
    private WindCodeChangeDto nextChange(List<WindCodeChangeDto> rows) {
        for (WindCodeChangeDto row : rows) {
            boolean hasPredecessor = false;
            for (WindCodeChangeDto candidate : rows) {
                if (candidate != row && row.getOldWindCode() != null
                        && row.getOldWindCode().equals(candidate.getNewWindCode())) {
                    hasPredecessor = true;
                    break;
                }
            }
            if (!hasPredecessor) {
                return row;
            }
        }
        return null;
    }
}

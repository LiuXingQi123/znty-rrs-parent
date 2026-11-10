package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.CrmwForbiddenOutCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwForbiddenAutoOutMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRMW关联禁投自动出库，只清退已经在库的组合
 *
 * 业务示例：
 * 1. 标的债券：A 公司发行的债券，是接受信用保护的对象
 * 2. CRMW 凭证：由 B 机构创设／提供信用保护
 * 3. 标的债券、A 公司、CRMW 凭证、B 机构任一已在禁止库时，退出对应凭证与标的的在库组合
 *
 * 禁止库入库记录由原业务生成，本任务不重复入禁止库，不改变另一端的禁投状态
 */
@Slf4j
@Service
public class CrmwForbiddenAutoOutService implements RrsScheduledTask {

    /** 定时任务编码 */
    public static final String TASK_CODE = "crmw_forbidden_auto_out";
    /** 定时任务展示名称 */
    public static final String TASK_NAME = "CRMW关联禁投自动出库";
    /** 扫描及原组合状态更新 */
    @Resource
    private CrmwForbiddenAutoOutMapper crmwForbiddenAutoOutMapper;
    /** CRMW自动出库日志写入 */
    @Resource
    private CrmwPoolAdjustMapper crmwPoolAdjustMapper;
    /** 按需取得本任务的Spring代理，让本类公开事务方法通过代理执行 */
    @Resource
    private ObjectProvider<CrmwForbiddenAutoOutService> selfProvider;

    /**
     * 返回任务配置使用的编码
     *
     * 执行顺序：
     * 1. 返回固定任务编码供调度器识别
     *
     * @return 任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 说明扫描范围和禁投清退规则
     *
     * 执行顺序：
     * 1. 返回无参数任务的角色、命中条件和事务口径
     *
     * @return 任务参数说明
     */
    @Override
    public String getParamHelp() {
        return "参数说明：无需扩展参数，请将 param_json 留空\n"
                + "业务示例：1. 标的债券：A 公司发行的债券；2. CRMW 凭证：由 B 机构创设／提供信用保护\n"
                + "扫描条件：有效CRMW池中未删除、审批通过的在库组合，一次读取全部有效组合\n"
                + "出库条件：标的债券、A公司、CRMW凭证、B机构任一在有效禁止库，合并全部命中原因\n"
                + "主体口径：标的与凭证分别取各自主档issuer_code，不扩展担保人或未确认ABS权益人\n"
                + "执行方式：禁投硬规则直接清退，不受人工调库的冻结期、锁定、开放日或调出限制影响\n"
                + "日志规则：只生成CRMW自动出库记录，不重复进入禁止库，不自动恢复已退出组合\n"
                + "事务规则：每个组合独立事务，单条失败只回滚该条并继续，结果汇总成功和失败数量\n"
                + "数据要求：标的、凭证主档及双方唯一主体代码须完整，缺失时记录该组合错误并继续，不推断主体";
    }

    /**
     * 执行关联禁投清退并返回可视化任务结果
     *
     * 执行顺序：
     * 1. 记录本轮开始时间和过程日志
     * 2. 调用私有方法扫描组合，单条异常只记录错误并继续
     * 3. 返回成功出库和单条失败数量，公共查询异常时返回任务级失败
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        log.info("任务开始：{}", TASK_NAME);
        detail.line("任务开始：" + TASK_NAME);
        try {
            // 1. 一次扫描全部组合，核心方法隔离每个组合的处理结果
            AutoOutSummary summary = doAutoOutForbidden(detail);
            // 2. 只将已经提交的出库数量作为影响条数
            String message = "扫描 " + summary.scannedCount + " 条组合，自动出库 " + summary.outCount
                    + " 条，失败 " + summary.failedCount + " 条";
            detail.line("任务结束：" + message);
            log.info("任务结束：{}，{}", TASK_NAME, message);
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, summary.outCount,
                    startTime, System.currentTimeMillis() - begin, detail.build());
        } catch (Exception e) {
            // 3. 公共查询失败时没有足够依据继续扫描，返回明确任务级错误
            String message = "任务公共查询失败：" + e.getMessage();
            log.error("{}失败：{}", TASK_NAME, message, e);
            detail.line("ERROR", message);
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, message,
                    startTime, System.currentTimeMillis() - begin, detail.build());
        }
    }

    /**
     * 一次读取全部有效组合，批量查询禁投依据并清退命中组合
     *
     * 组合表通常一千多条、最多不到一万条，一次读取即可，不做游标分页
     * 证券主档和禁止库仍按本轮实际代码批量查询，避免逐组合访问大表
     *
     * 执行顺序：
     * 1. 读取全部有效组合，无组合时直接返回零数量
     * 2. 将标的和凭证代码去重后一次读取主档
     * 3. 合并主档中的有效主体代码，一次读取本轮有效禁止库状态
     * 4. 逐组合核验依据并合并原因，命中后在单条事务中退出并写日志
     * 5. 单条失败时记录错误并继续处理，最后返回成功和失败数量
     *
     * @param detail 本轮过程日志
     * @return 本轮扫描和实际出库数量
     */
    private AutoOutSummary doAutoOutForbidden(TaskDetailLog detail) {
        detail.line("扫描条件：ip_pool_status_crmw.is_deleted=0、audit_status=20、pool_type=crmw；投资池未删除且类型crmw");
        detail.line("禁止库条件：ip_pool_status.is_deleted=0、audit_status=20；投资池未删除且类型forbidden");
        detail.line("执行口径：四类对象任一命中即退出原组合，单组合事务，单条失败继续；不检查人工调出限制，不新增禁止库记录");
        // 1. 组合数量不足一万，一次读取全部候选，不再分页或排序
        List<CrmwForbiddenOutCandidateDto> rows = crmwForbiddenAutoOutMapper.queryCrmwInPoolList();
        AutoOutSummary summary = new AutoOutSummary();
        summary.scannedCount = rows.size();
        if (rows.isEmpty()) {
            return summary;
        }
        ScheduledAdjustBatchNoContext batchContext = new ScheduledAdjustBatchNoContext();
        // 2. 同一证券在多个组合中只查询一次，控制百万级主档的访问范围
        Set<String> codes = new LinkedHashSet<>();
        for (CrmwForbiddenOutCandidateDto row : rows) {
            codes.add(row.getSecurityCode());
            codes.add(row.getCrmwScode());
        }
        Map<String, SecurityInfoBo> securities = new HashMap<>();
        for (SecurityInfoBo security : crmwForbiddenAutoOutMapper.querySecurityIssuerList(new ArrayList<>(codes))) {
            securities.put(security.getWindCode(), security);
        }
        // 3. 准备批量查询范围，残缺主体留到对应组合处理时明确报错
        for (SecurityInfoBo security : securities.values()) {
            if (StringUtils.hasText(security.getIssuerCode()) && !security.getIssuerCode().contains(",")) {
                codes.add(security.getIssuerCode());
            }
        }
        Set<String> forbiddenCodes = new HashSet<>(crmwForbiddenAutoOutMapper.queryForbiddenCodeList(new ArrayList<>(codes)));
        // 4. 每个组合独立处理，不让一条主档或日志异常中断后续组合
        for (CrmwForbiddenOutCandidateDto row : rows) {
            String context = "CRMW[" + row.getCrmwScode() + "]标的[" + row.getSecurityCode() + "]池[" + row.getTargetPoolId() + "]";
            try {
                SecurityInfoBo bond = securities.get(row.getSecurityCode());
                SecurityInfoBo crmw = securities.get(row.getCrmwScode());
                if (bond == null || crmw == null || !"crmw".equals(crmw.getSecurityType())) {
                    throw new BizException("标的或CRMW凭证主档缺失，或凭证类型不是crmw");
                }
                if (!StringUtils.hasText(bond.getIssuerCode()) || bond.getIssuerCode().contains(",")
                        || !StringUtils.hasText(crmw.getIssuerCode()) || crmw.getIssuerCode().contains(",")) {
                    throw new BizException("标的发行主体或创设方／保护方的唯一主体代码缺失或不正确");
                }
                List<String> reasons = new ArrayList<>();
                if (forbiddenCodes.contains(row.getSecurityCode())) {
                    reasons.add("标的债券[" + row.getSecurityCode() + "]已在禁止库");
                }
                if (forbiddenCodes.contains(bond.getIssuerCode())) {
                    reasons.add("标的发行主体[" + bond.getIssuerCode() + "]已在禁止库");
                }
                if (forbiddenCodes.contains(row.getCrmwScode())) {
                    reasons.add("CRMW凭证[" + row.getCrmwScode() + "]已在禁止库");
                }
                if (forbiddenCodes.contains(crmw.getIssuerCode())) {
                    reasons.add("创设方／保护方[" + crmw.getIssuerCode() + "]已在禁止库");
                }
                if (reasons.isEmpty()) {
                    continue;
                }
                // 5. 准备日志并通过任务代理调用事务方法，失败只回滚当前组合
                int changed = autoOutCombination(row, reasons, batchContext);
                summary.outCount += changed;
                detail.line(context + (changed == 0 ? "原组合已出库，跳过" : "出库已提交，" + String.join("；", reasons)));
            } catch (Exception e) {
                summary.failedCount++;
                log.error("{}处理失败，保留原组合并继续：{}", context, e.getMessage(), e);
                detail.line("ERROR", context + "处理失败，保留原组合并继续：" + e.getMessage());
            }
        }
        return summary;
    }

    /**
     * 准备单组合日志并通过任务代理调用本类的公开事务方法出库
     *
     * 执行顺序：
     * 1. 填写系统自动出库信息、全部命中原因和组合批次号
     * 2. 通过Spring代理调用本类公开方法，状态和日志一起提交或回滚
     *
     * @param row 已核验且命中禁投条件的组合
     * @param reasons 本条全部禁投原因
     * @param batchContext 本轮批次号上下文
     * @return 实际出库数量，0或1
     */
    private int autoOutCombination(CrmwForbiddenOutCandidateDto row, List<String> reasons,
                                    ScheduledAdjustBatchNoContext batchContext) {
        // 1. 沿用现有CRMW日志和批次规则，只填写调整原因
        row.setAdjustType(AdjustType.AUTO.getCode());
        row.setAdjustMode(AdjustMode.OUT.getCode());
        row.setPoolType(PoolType.CRMW.getCode());
        row.setAuditStatus(AuditStatus.APPROVED.getCode());
        row.setAdjusterId("0");
        row.setAdjusterName("系统");
        row.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(TASK_NAME, reasons.toArray(new String[0])));
        row.setSubmitTime(batchContext.getSubmitTime());
        row.setAdjustBatchNo(batchContext.resolveCrmwBatchNo(row.getCrmwScode(), row.getSecurityCode(),
                row.getTargetPoolId(), AdjustMode.OUT.getCode()));
        // 2. 通过任务的Spring代理进入事务，直接使用this调用不会经过事务拦截
        return selfProvider.getObject().editCrmwForbiddenOutByJob(row);
    }

    /**
     * 为关联禁投任务退出一条已经核验的CRMW组合
     *
     * execute不加整体事务，本方法必须通过selfProvider取得的Spring代理逐条调用
     * 异常由Spring回滚当前组合，再由外层扫描记录错误并继续其他组合
     *
     * 执行顺序：
     * 1. 软删除仍有效的原状态，已退出时返回0且不重复生成日志
     * 2. 写入已准备的系统自动出库日志，失败时抛出异常回滚本条状态
     *
     * @param row 已核验禁投依据并填写日志字段的组合
     * @return 实际出库数量，0或1
     */
    @Transactional(rollbackFor = Exception.class)
    public int editCrmwForbiddenOutByJob(CrmwForbiddenOutCandidateDto row) {
        // 1. 按原状态主键和完整组合出库，不影响后续重新入库的新状态
        if (crmwForbiddenAutoOutMapper.editCrmwPoolStatusOut(row) == 0) {
            return 0;
        }
        // 2. 写入失败向外抛出，由注解事务撤销本条已软删的池状态
        if (crmwPoolAdjustMapper.addAdjustLog(row) != 1) {
            throw new BizException("出库日志写入失败");
        }
        return 1;
    }

    /** 本轮扫描、已提交出库和失败数量 */
    private static class AutoOutSummary {
        /** 本轮扫描的有效组合数量 */
        private int scannedCount;
        /** 已提交的实际出库数量 */
        private int outCount;
        /** 主档异常或出库失败的组合数量 */
        private int failedCount;
    }
}

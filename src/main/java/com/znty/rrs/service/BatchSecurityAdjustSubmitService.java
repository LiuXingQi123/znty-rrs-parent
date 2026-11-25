package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchSecurityAdjustSubmitMapper;
import com.znty.rrs.service.SecurityPoolAdjustService.ExcelSubmitGroup;
import com.znty.rrs.service.SecurityPoolAdjustService.ExcelSubmitRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 处理 Excel 证券调库的批量提交和直通落池
 *
 * {@link SecurityPoolExcelImportService} 提交证券导入或清空目标池时调用本类
 * 复用 {@link SecurityPoolAdjustService} 复核每组证券、评级主体和流程，准备直通或逐组提交的数据
 * 可直通的组先批量保存调库日志、审批步骤和证券快照，再更新池状态，需要审批或单独处理材料的组逐组提交
 * 同一证券重复出现时先写入前一组，让后续组读取已经生效的池状态和 ABS 主体身份
 */
@Service
public class BatchSecurityAdjustSubmitService {

    /** 每批写入证券调库记录的最大条数 */
    private static final int BATCH_SIZE = 200;

    /** 批量写入证券调库日志、步骤、快照和池状态 */
    @Resource
    private BatchSecurityAdjustSubmitMapper batchSecurityAdjustSubmitMapper;

    /** 复用证券调库的提交校验和 ABS 身份处理 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /**
     * 提交已确认的证券 Excel 导入或清空项
     *
     * 执行顺序：
     * 1. 复核每组主体与流程，生成可批量直通的数据
     * 2. 批量写入日志、步骤和快照
     * 3. 按原顺序应用直通池状态，保留 ABS 身份生效与同步结论
     *
     * @param requests 本次导入行或清空主券
     * @param ratingData 本次已读取的证券与评级候选
     * @param clear 是否为清空出库
     * @return 与各请求顺序对应的调库日志
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AdjustSubmitDto> submit(List<SecurityPoolAdjustSubmitReq> requests,
                                        BatchSecurityCheckData ratingData, boolean clear) {
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExcelSubmitGroup> groups = securityPoolAdjustService.prepareExcelSubmitGroups(requests, ratingData, clear);
        List<AdjustSubmitDto> results = new ArrayList<>();
        List<ExcelSubmitGroup> pending = new ArrayList<>();
        Set<String> pendingCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ExcelSubmitGroup group : groups) {
            // 生成直通计划，其他流程仍使用原处理方式
            securityPoolAdjustService.planExcelDirectGroup(group);
            if (group.rows == null) {
                // 先完成前续直通，保持与后续审批或特殊材料分支的提交顺序
                flushDirectGroups(pending, results);
                pendingCodes.clear();
                results.add(securityPoolAdjustService.submitExcelGroupIndividually(group));
                continue;
            }
            Set<String> codes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            codes.add(group.request.getSecurityCode());
            group.rows.forEach(row -> codes.add(row.log.getSecurityCode()));
            if (!Collections.disjoint(pendingCodes, codes)) {
                // 同码重复提交先落旧组，ABS 身份与池状态按原顺序可见
                flushDirectGroups(pending, results);
                pendingCodes.clear();
            }
            pending.add(group);
            pendingCodes.addAll(codes);
        }
        // 写入剩余的直通组
        flushDirectGroups(pending, results);
        return results;
    }

    /**
     * 按请求顺序持久化一组互不重叠的直通证券
     *
     * 执行顺序：
     * 1. 逐项新增调库日志列表
     * 2. 逐项用已回填日志主键生成证券和 ABS 申请快照
     * 3. 快照先于池状态生效，ABS 每组都保留生效前后的原处理顺序
     *
     * @param groups 待写入的直通组
     * @param results 当前提交结果
     */
    private void flushDirectGroups(List<ExcelSubmitGroup> groups, List<AdjustSubmitDto> results) {
        if (groups.isEmpty()) {
            return;
        }
        List<IpAdjustLogBo> logs = groups.stream().flatMap(group -> group.rows.stream())
                .map(row -> row.log).collect(Collectors.toList());
        Set<Long> insertedIds = new HashSet<>();
        for (int offset = 0; offset < logs.size(); offset += BATCH_SIZE) {
            List<IpAdjustLogBo> chunk = logs.subList(offset, Math.min(offset + BATCH_SIZE, logs.size()));
            if (batchSecurityAdjustSubmitMapper.addAdjustLogs(chunk) != chunk.size()) {
                throw new BizException("证券调库日志批量写入失败");
            }
            for (IpAdjustLogBo log : chunk) {
                if (log.getId() == null || !insertedIds.add(log.getId())) {
                    throw new BizException("证券调库日志主键回填失败");
                }
            }
        }

        List<IpAdjustStepBo> steps = new ArrayList<>();
        List<AdjustSecuritySnapshotBo> snapshots = new ArrayList<>();
        for (ExcelSubmitGroup group : groups) {
            List<Long> ids = new ArrayList<>();
            for (ExcelSubmitRow row : group.rows) {
                ids.add(row.log.getId());
                for (IpAdjustStepBo step : row.steps) {
                    step.setAdjustLogId(row.log.getId());
                    steps.add(step);
                }
            }
            securityPoolAdjustService.collectExcelSubmitSnapshots(group, snapshots);
            AdjustSubmitDto result = new AdjustSubmitDto();
            result.setSecurityCode(group.request.getSecurityCode());
            result.setSubmitCount(ids.size());
            result.setLogIds(ids);
            results.add(result);
        }
        for (int offset = 0; offset < steps.size(); offset += BATCH_SIZE) {
            List<IpAdjustStepBo> chunk = steps.subList(offset, Math.min(offset + BATCH_SIZE, steps.size()));
            if (batchSecurityAdjustSubmitMapper.addAdjustSteps(chunk) != chunk.size()) {
                throw new BizException("证券调库流程步骤批量写入失败");
            }
        }
        for (int offset = 0; offset < snapshots.size(); offset += BATCH_SIZE) {
            List<AdjustSecuritySnapshotBo> chunk = snapshots.subList(offset, Math.min(offset + BATCH_SIZE, snapshots.size()));
            if (batchSecurityAdjustSubmitMapper.addSecuritySnapshots(chunk) != chunk.size()) {
                throw new BizException("证券申请快照批量写入失败");
            }
        }
        // 快照先于池状态生效，ABS 每组都保留生效前后的原处理顺序
        applyDirectGroups(groups);
        groups.clear();
    }

    /**
     * 合并普通证券池状态写入，ABS 在身份确认和人工结论之间写入本组池状态
     *
     * 执行顺序：
     * 1. 按证券和池组合批量结束旧状态，调入项新增生效状态
     * 2. 批量落池后按原业务记录 ABS 人工结论
     * 3. 完成最后一批普通证券状态
     *
     * @param groups 已保存日志和快照的直通组
     */
    private void applyDirectGroups(List<ExcelSubmitGroup> groups) {
        List<IpAdjustLogBo> ordinaryLogs = new ArrayList<>();
        for (ExcelSubmitGroup group : groups) {
            List<IpAdjustLogBo> logs = group.rows.stream().map(row -> row.log).collect(Collectors.toList());
            if (group.ordinary) {
                ordinaryLogs.addAll(logs);
                continue;
            }
            // ABS 前先完成前面的普通券状态，再按原顺序执行本组身份处理
            applyPoolStatuses(ordinaryLogs);
            ordinaryLogs.clear();
            securityPoolAdjustService.approveExcelAbsGroup(group, logs);
            // 按证券和池组合批量结束旧状态，调入项新增生效状态
            applyPoolStatuses(logs);
            securityPoolAdjustService.recordExcelAbsDecisions(logs);
        }
        // 完成最后一批普通证券状态
        applyPoolStatuses(ordinaryLogs);
    }

    /**
     * 按证券和池组合批量结束旧状态，调入项新增生效状态
     *
     * 执行顺序：
     * 1. 按证券代码和目标池收集记录，达到分组上限或同一组合重复时先写入当前小批
     * 2. 循环结束后写入余下的记录，保证同一小批内证券和池的组合互不重复
     *
     * @param logs 已通过复核的直通日志
     */
    private void applyPoolStatuses(List<IpAdjustLogBo> logs) {
        List<IpAdjustLogBo> chunk = new ArrayList<>();
        Set<List<Object>> keys = new HashSet<>();
        // 1. 按证券代码和目标池收集记录，达到分组上限或同一组合重复时先写入当前小批
        for (IpAdjustLogBo log : logs) {
            List<Object> key = Arrays.asList(log.getSecurityCode().toUpperCase(Locale.ROOT), log.getTargetPoolId());
            if (chunk.size() == BATCH_SIZE || keys.contains(key)) {
                // 同券同池重复项分开应用，后续项仍能结束前一项写出的状态
                writePoolStates(chunk);
                chunk.clear();
                keys.clear();
            }
            keys.add(key);
            log.setAdjustLogId(log.getId());
            chunk.add(log);
        }
        // 2. 循环结束后写入余下的记录，保证同一小批内证券和池的组合互不重复
        if (!chunk.isEmpty()) {
            // 写入最后一个池状态分片
            writePoolStates(chunk);
        }
    }

    /**
     * 写入证券与池组合互不重复的一批状态
     *
     * 执行顺序：
     * 1. 证券调入池状态批量写入失败时终止处理
     *
     * @param rows 直通调库日志
     */
    private void writePoolStates(List<IpAdjustLogBo> rows) {
        List<IpAdjustLogBo> outbound = rows.stream()
                .filter(log -> AdjustMode.OUT.getCode().equals(log.getAdjustMode())).collect(Collectors.toList());
        if (!outbound.isEmpty()) {
            batchSecurityAdjustSubmitMapper.deletePoolStatusesSoft(outbound);
        }
        List<IpAdjustLogBo> inbound = rows.stream()
                .filter(log -> AdjustMode.IN.getCode().equals(log.getAdjustMode())).collect(Collectors.toList());
        if (!inbound.isEmpty() && batchSecurityAdjustSubmitMapper.addPoolStatuses(inbound) != inbound.size()) {
            throw new BizException("证券调入池状态批量写入失败");
        }
    }
}

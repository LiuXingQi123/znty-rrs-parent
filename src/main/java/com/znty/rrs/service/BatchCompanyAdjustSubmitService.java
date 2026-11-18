package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchCompanyAdjustSubmitMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.service.ForbiddenPoolAdjustService.ExcelCompanyBatchContext;
import com.znty.rrs.service.ForbiddenPoolAdjustService.ExcelCompanySubmitRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Excel 主体调库的批量提交与直通落池
 */
@Service
public class BatchCompanyAdjustSubmitService {

    private static final int BATCH_SIZE = 200;

    @Resource
    private BatchCompanyAdjustSubmitMapper batchCompanyAdjustSubmitMapper;
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    /**
     * 批量提交已确认的 Excel 主体导入或清空项
     *
     * 1. 批量读取主体主档和投资池
     * 2. 按当前流程配置生成日志和初始步骤
     * 3. 批量写入日志、流程步骤及直通池状态
     *
     * @param requests 按主体分组的调库提交请求
     * @return 按主体及调库方向顺序生成的日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> submit(List<ForbiddenPoolAdjustSubmitReq> requests) {
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> codes = requests.stream().map(ForbiddenPoolAdjustSubmitReq::getCompanyCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, ForbiddenPoolAdjustDto> companies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int start = 0; start < codes.size(); start += BATCH_SIZE) {
            List<String> chunk = codes.subList(start, Math.min(start + BATCH_SIZE, codes.size()));
            for (ForbiddenPoolAdjustDto company : batchCompanyAdjustSubmitMapper.queryCompanyList(chunk)) {
                companies.putIfAbsent(company.getCompanyCode(), company);
            }
        }
        Map<Long, InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        ExcelCompanyBatchContext context = new ExcelCompanyBatchContext(companies, pools);
        List<Long> ids = new ArrayList<>();
        List<ExcelCompanySubmitRow> rows = new ArrayList<>();
        for (ForbiddenPoolAdjustSubmitReq request : requests) {
            // 同一流程的定义、活跃版本和节点在本次提交内共用
            List<ExcelCompanySubmitRow> companyRows = hasAttachmentReferences(request) ? null
                    : forbiddenPoolAdjustService.planExcelCompanySubmit(request, context);
            if (companyRows == null) {
                // 先落前面的批量结果，再按原规则处理本组的特殊流程或附件
                ids.addAll(persistRows(rows));
                rows.clear();
                ids.addAll(forbiddenPoolAdjustService.addCompanyExcelAdjustLog(request).getLogIds());
            } else {
                rows.addAll(companyRows);
            }
        }
        // 批量写日志后使用回填的主键关联步骤和在池状态
        ids.addAll(persistRows(rows));
        return ids;
    }

    /**
     * 写入日志、初始流程步骤和已生效池状态
     *
     * @param rows 按原提交顺序生成的日志及步骤
     * @return 已写入的日志 ID
     */
    private List<Long> persistRows(List<ExcelCompanySubmitRow> rows) {
        List<IpAdjustLogBo> logs = rows.stream().map(row -> row.log).collect(Collectors.toList());
        for (int start = 0; start < logs.size(); start += BATCH_SIZE) {
            List<IpAdjustLogBo> chunk = logs.subList(start, Math.min(start + BATCH_SIZE, logs.size()));
            if (batchCompanyAdjustSubmitMapper.addAdjustLogs(chunk) != chunk.size()) {
                throw new BizException("主体调库日志批量写入失败");
            }
        }
        List<Long> ids = logs.stream().map(IpAdjustLogBo::getId).collect(Collectors.toList());
        if (ids.contains(null) || new HashSet<>(ids).size() != ids.size()) {
            throw new BizException("主体调库日志主键回填失败");
        }
        List<IpAdjustStepBo> steps = new ArrayList<>();
        List<IpAdjustLogBo> approved = new ArrayList<>();
        for (ExcelCompanySubmitRow row : rows) {
            for (IpAdjustStepBo step : row.steps) {
                step.setAdjustLogId(row.log.getId());
                steps.add(step);
            }
            if (AuditStatus.APPROVED.getCode().equals(row.log.getAuditStatus())) {
                row.log.setAdjustLogId(row.log.getId());
                approved.add(row.log);
            }
        }
        for (int start = 0; start < steps.size(); start += BATCH_SIZE) {
            List<IpAdjustStepBo> chunk = steps.subList(start, Math.min(start + BATCH_SIZE, steps.size()));
            if (batchCompanyAdjustSubmitMapper.addAdjustSteps(chunk) != chunk.size()) {
                throw new BizException("主体调库流程步骤批量写入失败");
            }
        }
        // 仅直通生效的调库项更新池状态
        applyApprovedRows(approved);
        return ids;
    }

    /**
     * 按原顺序应用直通项，同一主体同池的多次操作分批执行
     *
     * @param approved 已生效的调库日志
     */
    private void applyApprovedRows(List<IpAdjustLogBo> approved) {
        List<IpAdjustLogBo> chunk = new ArrayList<>();
        Set<List<Object>> keys = new HashSet<>();
        for (IpAdjustLogBo log : approved) {
            List<Object> key = Arrays.asList(log.getSecurityCode(), log.getTargetPoolId());
            if (chunk.size() == BATCH_SIZE || keys.contains(key)) {
                // 同池操作分批，后一次仍可以结束前一次写入的在池状态
                writePoolStates(chunk);
                chunk.clear();
                keys.clear();
            }
            keys.add(key);
            chunk.add(log);
        }
        if (!chunk.isEmpty()) {
            // 写入最后一批池状态
            writePoolStates(chunk);
        }
    }

    /**
     * 结束旧在池状态并新增本批直通调入状态
     *
     * @param rows 主体和池组合不重复的直通调库日志
     */
    private void writePoolStates(List<IpAdjustLogBo> rows) {
        List<IpAdjustLogBo> outbound = rows.stream()
                .filter(log -> AdjustMode.OUT.getCode().equals(log.getAdjustMode())).collect(Collectors.toList());
        if (!outbound.isEmpty()) {
            batchCompanyAdjustSubmitMapper.deletePoolStatusesSoft(outbound);
        }
        List<IpAdjustLogBo> inbound = rows.stream()
                .filter(log -> AdjustMode.IN.getCode().equals(log.getAdjustMode()))
                .collect(Collectors.toList());
        if (!inbound.isEmpty() && batchCompanyAdjustSubmitMapper.addPoolStatuses(inbound) != inbound.size()) {
            throw new BizException("主体调入池状态写入失败，请刷新后重试");
        }
    }

    /**
     * 判断是否需要原入口处理引用的附件
     *
     * @param request 主体提交请求
     * @return 是否传入报告或材料
     */
    private boolean hasAttachmentReferences(ForbiddenPoolAdjustSubmitReq request) {
        return request.getItems() != null && request.getItems().stream().anyMatch(item ->
                !CollectionUtils.isEmpty(item.getCreditReportFileIndexes())
                        || !CollectionUtils.isEmpty(item.getMaterialFileIndexes())
                        || !CollectionUtils.isEmpty(item.getCreditReportSourceAttachmentIds())
                        || !CollectionUtils.isEmpty(item.getMaterialSourceAttachmentIds()));
    }
}

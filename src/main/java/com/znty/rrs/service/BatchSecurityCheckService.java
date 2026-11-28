package com.znty.rrs.service;

import com.znty.rrs.common.constants.BusinessSwitches;
import com.znty.rrs.common.constants.ProductInboundPoolCodes;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.common.util.RatingComparator;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.WindIssuerRatingBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.Row;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.ScheduledSecurity;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.znty.rrs.service.SecurityPoolCheckRules.SIMPLE_INBOUND_HISTORY_DAYS;

/**
 * 为批量调库和自动补债校验证券调入调出条件
 *
 * 一次读取本批证券及同券跨市场代码的主档、在途流程、池状态、关系池和 ABS 主体等校验所需数据
 * 逐券复用 {@link SecurityPoolCheckRules} 的规则，返回各调库项的校验结果和流程候选
 * 自动补债按本轮摘牌日截止日期复核候选，并区分随已生效 Excel 主体决定处理的请求
 */
@Slf4j
@Service
public class BatchSecurityCheckService {

    /** 单券规则和审批流程判定 */
    @Resource
    private SecurityPoolCheckRules securityPoolCheckRules;

    /** 批次校验取数 */
    @Resource
    private BatchSecurityCheckMapper batchSecurityCheckMapper;

    /** 查询配套调出项的 Excel 入池保护 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 主体 Excel 调出依据 */
    @Resource
    private ExcelPoolExclusionService excelPoolExclusionService;

    /** ABS 主体冲突结果处理 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** 投资池查询 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 证券市场查询 */
    @Resource
    private SecurityMarketMapper securityMarketMapper;

    /** 有效外评机构配置 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /** 信用债分级规则数据 */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /**
     * 批量页面沿用单券规则校验本批调库项
     *
     * 执行顺序：
     * 1. 按批量页面口径合并同券主项，再复用证券调库校验
     *
     * @param requests 本批调库请求
     * @return 按原请求顺序返回校验结果，被合并的请求用空明细占位
     */
    List<AdjustCheckDto> checkSecurityBatch(List<AdjustCheckReq> requests) {
        // 由同券主项展开各市场代码，并保留原请求的结果位置
        return checkSecurityBatch(requests, null, Collections.emptySet(), true);
    }

    /**
     * 校验本轮自动补债请求
     *
     * 执行顺序：
     * 1. 按本轮摘牌日截止日期逐条校验，并区分是否跟随 Excel 主体决定
     *
     * @param requests 同轮待处理的调库请求
     * @param maturityCutoff 本轮初筛使用的摘牌日截止日期
     * @param excelCompanyRequests 随已生效 Excel 主体决定处理的请求
     * @return 按原请求顺序返回的校验结果，包含通过项和失败项
     */
    List<AdjustCheckDto> checkCompanyFollowBatch(List<AdjustCheckReq> requests, String maturityCutoff,
                                                Set<AdjustCheckReq> excelCompanyRequests) {
        // 自动补债按原请求逐条校验，累计本轮预计容量
        return checkSecurityBatch(requests, maturityCutoff, excelCompanyRequests, false);
    }

    /**
     * 校验本批证券调库请求并保留逐券结果
     *
     * 批量页面按下标将返回结果和原请求配对，已合并的请求用空明细占位
     *
     * 执行顺序：
     * 1. 请求为空时直接返回，逐条检查证券代码和调库项是否齐全
     * 2. 批量读取本次校验所需的证券、投资池及在池和在途状态
     * 3. 识别同一 ABS 的主体冲突，批量页面同时确定同券主项和组内冲突
     * 4. 按顺序校验各请求并写入已发现的冲突，自动补债记录单项异常并为通过的组预留容量
     *
     * @param requests 本批调库请求
     * @param maturityCutoff 自动补债的摘牌日截止日期，批量页面传 null
     * @param excelCompanyRequests 随已生效 Excel 主体决定处理的请求
     * @param mergeRelatedSelections 是否合并批量页面中同时勾选的同券代码
     * @return 与请求顺序对应的校验结果，被合并的请求返回空明细，请求列表为空时返回空列表
     */
    private List<AdjustCheckDto> checkSecurityBatch(List<AdjustCheckReq> requests, String maturityCutoff,
                                                    Set<AdjustCheckReq> excelCompanyRequests,
                                                    boolean mergeRelatedSelections) {
        boolean scheduled = maturityCutoff != null;
        // 1. 检查本批请求的证券代码和调库项
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        for (AdjustCheckReq request : requests) {
            if (request.getSecurityCode() == null || request.getSecurityCode().isEmpty()) {
                throw new BizException("证券代码不能为空");
            }
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new BizException("调库项不能为空");
            }
        }
        // 2. 批量读取本次校验共用的数据
        BatchSecurityCheckData data = loadBatchCheckData(requests, maturityCutoff, false, false);
        // 3. 检查本批 ABS 主体冲突，并确认批量页面的同券主项和主体选择
        Set<String> conflictingAbsCodes = findConflictingAbsCodes(requests, data);
        // 批量页面用已加载的同券关系选主项，自动补债按原请求逐条校验
        Map<Integer, Integer> mergedSelections = mergeRelatedSelections
                ? findMergedRelatedSelections(requests, data) : Collections.emptyMap();
        // 比较本组已勾选的主体，避免由主项的选择覆盖其他行
        Map<Integer, String> relatedSelectionFailures = findRelatedSelectionFailures(requests, mergedSelections);
        // 4. 按原请求顺序生成校验结果
        List<AdjustCheckDto> results = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            if (mergedSelections.containsKey(index)) {
                // 用空明细保留已合并请求的位置，供调用方按下标回填
                AdjustCheckDto merged = new AdjustCheckDto();
                merged.setItems(Collections.emptyList());
                results.add(merged);
                continue;
            }
            AdjustCheckReq request = requests.get(index);
            // 区分普通单券校验和跟随 Excel 主体决定的校验
            boolean excelCompany = excelCompanyRequests.contains(request);
            AdjustCheckDto result;
            try {
                result = excelCompany ? securityPoolCheckRules.checkExcelCompanyFollowItem(request, data)
                        : securityPoolCheckRules.checkBatchItem(request, false, data);
            } catch (BizException e) {
                if (!scheduled) {
                    throw e;
                }
                // 自动补债记录当前请求失败，继续校验后面的请求
                AdjustCheckDto.CheckResultItem failure = new AdjustCheckDto.CheckResultItem();
                failure.setSecurityCode(request.getSecurityCode());
                failure.setTargetPoolId(request.getItems().get(0).getTargetPoolId());
                failure.setFailReasons(Collections.singletonList(e.getMessage()));
                result = new AdjustCheckDto();
                result.setItems(Collections.singletonList(failure));
            }
            // 将同码 ABS 的主体冲突和同券组的选择冲突写入各条校验明细
            if (conflictingAbsCodes.contains(request.getSecurityCode())) {
                String failure = "同一 ABS[" + request.getSecurityCode()
                        + "]在同批申请中选择了不同主体，请统一后重新校验";
                result.getItems().forEach(item -> absIdentityService.applyCheckFailure(item, failure));
            }
            String relatedSelectionFailure = relatedSelectionFailures.get(index);
            if (relatedSelectionFailure != null) {
                result.getItems().forEach(item -> absIdentityService.applyCheckFailure(item, relatedSelectionFailure));
            }
            if (scheduled) {
                // 复核自动补债条件，只为通过的组更新本轮预计容量
                prepareScheduledInbound(result, data, excelCompany);
            }
            results.add(result);
        }
        return results;
    }

    /**
     * 找出可以由同券主项展开的批量请求
     *
     * 执行顺序：
     * 1. 按请求顺序处理仅有一条调库项的请求，在相同目标池和方向下查找已登记的同券主项
     * 2. 被覆盖的请求记录对应主项，未被覆盖的请求登记它能展开的关联代码
     *
     * @param requests 本批调库请求
     * @param data 批量加载的校验数据
     * @return 被合并请求的位置及对应主项位置，没有重复勾选时返回空映射
     */
    private Map<Integer, Integer> findMergedRelatedSelections(List<AdjustCheckReq> requests,
                                                              BatchSecurityCheckData data) {
        Map<String, Integer> sourceByRelatedCode = new HashMap<>();
        Map<Integer, Integer> mergedSelections = new LinkedHashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            AdjustCheckReq request = requests.get(index);
            if (request.getItems().size() != 1) {
                continue;
            }
            AdjustCheckReq.CheckItem item = request.getItems().get(0);
            String scope = item.getTargetPoolId() + "|" + item.getAdjustMode() + "|";
            Integer sourceIndex = sourceByRelatedCode.get(scope + request.getSecurityCode());
            if (sourceIndex != null) {
                mergedSelections.put(index, sourceIndex);
                continue;
            }
            for (String relatedCode : data.relatedCodes.getOrDefault(request.getSecurityCode(), Collections.emptyList())) {
                sourceByRelatedCode.putIfAbsent(scope + relatedCode, index);
            }
        }
        return mergedSelections;
    }

    /**
     * 检查同券组的担保人、权益人和自选权益人选择是否一致
     *
     * 执行顺序：
     * 1. 按请求顺序将每个被合并请求的主体选择与主项比较
     * 2. 为主体选择不同的组记录第一个冲突，提示用户统一选项
     *
     * @param requests 本批调库请求
     * @param mergedSelections 被合并请求的位置及对应主项位置
     * @return 主项位置及冲突说明，没有冲突时返回空映射
     */
    private Map<Integer, String> findRelatedSelectionFailures(List<AdjustCheckReq> requests,
                                                               Map<Integer, Integer> mergedSelections) {
        Map<Integer, String> failures = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : mergedSelections.entrySet()) {
            AdjustCheckReq selected = requests.get(entry.getKey());
            AdjustCheckReq source = requests.get(entry.getValue());
            // 空值和空白都表示未选择，比较页面实际提交的三个主体选项
            if (!sameCompanyCode(source.getGuarantorCode(), selected.getGuarantorCode())
                    || !sameCompanyCode(source.getRightsHolderCode(), selected.getRightsHolderCode())
                    || !sameCompanyCode(source.getSelfSelectedRightsHolderCode(), selected.getSelfSelectedRightsHolderCode())) {
                failures.putIfAbsent(entry.getValue(), "同券证券[" + source.getSecurityCode() + "、"
                        + selected.getSecurityCode() + "]选择的担保人或权益人不一致，请统一后重新校验");
            }
        }
        return failures;
    }

    /**
     * 比较两项主体代码选择
     *
     * 执行顺序：
     * 1. 将 null 视为空串，去掉首尾空白后比较代码
     *
     * @param first 主项选择的主体代码
     * @param second 关联项选择的主体代码
     * @return true=主体选择相同，false=主体选择不同
     */
    private boolean sameCompanyCode(String first, String second) {
        return Objects.toString(first, "").trim().equals(Objects.toString(second, "").trim());
    }

    /**
     * 同一 ABS 的各次选择统一按最终主体比较，缺省时使用首个候选
     *
     * 执行顺序：
     * 1. 逐条识别 ABS，并根据自选权益人、关联权益人、已选评级主体和默认候选确定主体代码
     * 2. 返回同一 ABS 选择了多个不同主体的证券代码集合
     *
     * @param requests 本批调库请求
     * @param data 本批当前状态
     * @return 本批存在不同评级主体选择的 ABS 证券代码集合，无冲突时为空集合
     */
    Set<String> findConflictingAbsCodes(List<AdjustCheckReq> requests, BatchSecurityCheckData data) {
        Map<String, Set<String>> selections = new HashMap<>();
        // 1. 逐条识别 ABS，并根据自选权益人、关联权益人、已选评级主体和默认候选确定主体代码
        for (AdjustCheckReq request : requests) {
            String code = data.resolveExcelSecurityCode(request.getSecurityCode());
            if (!CreditBondSpecialInboundRule.isAbs(data.securities.get(code))) {
                continue;
            }
            String selected = request.getSelfSelectedRightsHolderCode();
            if (!StringUtils.hasText(selected)) {
                selected = request.getRightsHolderCode();
            }
            if (!StringUtils.hasText(selected) && request.getSelectedRatingCompany() != null) {
                selected = request.getSelectedRatingCompany().getCompanyCode();
            }
            if (!StringUtils.hasText(selected)) {
                selected = data.ratingCompanies.getOrDefault(code, Collections.emptyList()).stream()
                        .findFirst().map(row -> row.getCompanyCode()).orElse(null);
            }
            if (StringUtils.hasText(selected)) {
                selections.computeIfAbsent(code, key -> new HashSet<>()).add(selected.trim());
            }
        }
        // 2. 返回同一 ABS 选择了多个不同主体的证券代码集合
        return selections.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * 复核自动落池的证券资格、材料、容量和 Excel 保护
     *
     * 执行顺序：
     * 1. 有 Excel 保护的组不会落库，不能把它的调入计入后续候选的在池状态
     * 2. 只有整组通过时，才更新下一组校验可见的在池状态及容量
     *
     * @param result 一只证券及其配套项的校验结果
     * @param data 本批当前状态
     * @param excelCompany 是否按生效的主体 Excel 决定执行
     */
    private void prepareScheduledInbound(AdjustCheckDto result, BatchSecurityCheckData data, boolean excelCompany) {
        List<AdjustCheckDto.CheckResultItem> items = result.getItems();
        if (items.isEmpty() || items.stream().anyMatch(item -> !item.isCanAdjust())) {
            return;
        }
        AdjustCheckDto.CheckResultItem primary = items.get(0);
        Map<Long, Integer> increments = new HashMap<>();
        for (AdjustCheckDto.CheckResultItem item : items) {
            InvestmentPoolBo pool = data.pools.get(item.getTargetPoolId());
            if (pool == null || !"enabled".equals(pool.getStatus()) || Integer.valueOf(1).equals(pool.getIsDeleted())
                    || CompanyBondSyncPolicy.isCrmwCombinationPool(pool)) {
                // 普通债任务只允许操作当前生效的普通池
                rejectScheduledItem(item, "目标池不存在、未启用或属于 CRMW 组合池");
                return;
            }
            boolean inbound = AdjustMode.IN.getCode().equals(item.getAdjustMode());
            if (inbound && !data.scheduledEligibleCodes.contains(item.getSecurityCode())
                    && (!excelCompany || !data.scheduledEligibleWithoutMaturityCodes.contains(item.getSecurityCode()))) {
                // 关联码同样受任务候选范围和到期门槛约束
                rejectScheduledItem(item, "证券不符合定时补债范围或距到期天数不足");
                return;
            }
            String issuer = data.securities.get(item.getSecurityCode()).getIssuerCode();
            if (inbound && data.excelCompanyOutPools.getOrDefault(issuer, Collections.emptySet()).contains(pool.getId())) {
                // 将自动任务的特有条件加入原调库项失败原因
                rejectScheduledItem(item, "发行主体已通过 Excel 调出目标池，不能自动补入");
                return;
            }
            if (!excelCompany && !ItemType.RELATED.getCode().equals(item.getItemTag()) && primary.getMaterialExemptionType() == null
                    // 判断池是否要求研究报告
                    && requiresReport(inbound ? pool.getInReportRestriction() : pool.getOutReportRestriction())
                    && !data.reportExemptIssuers.contains(issuer)) {
                // 保留原规则的材料豁免，缺少材料则跳过整组
                rejectScheduledItem(item, "目标池要求研究报告且本次没有可用的报告豁免");
                return;
            }
            if (inbound && !excelCompany) {
                int count = data.counts.getOrDefault(pool.getId(), 0) + increments.merge(pool.getId(), 1, Integer::sum);
                if (pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0 && count > pool.getMaxCapacity()) {
                    // 联动和关联码也纳入同一组的容量累计
                    rejectScheduledItem(item, "目标投资池已达持仓上限（" + pool.getMaxCapacity() + "）");
                    return;
                }
            }
        }
        // 1. 有 Excel 保护的组不会落库，不能把它的调入计入后续候选的在池状态
        for (AdjustCheckDto.CheckResultItem item : items) {
            if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && securityPoolAdjustMapper.queryExcelProtectedPoolIds(item.getSecurityCode()).contains(item.getTargetPoolId())) {
                // 将自动任务的特有条件加入原调库项失败原因
                rejectScheduledItem(item, "涉及 Excel 导入保护池，跳过本组调库");
                return;
            }
        }
        // 2. 只有整组通过时，才更新下一组校验可见的在池状态及容量
        for (AdjustCheckDto.CheckResultItem item : items) {
            Set<Long> pools = data.activePools.computeIfAbsent(item.getSecurityCode(), code -> new HashSet<>());
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                pools.add(item.getTargetPoolId());
                data.counts.merge(item.getTargetPoolId(), 1, Integer::sum);
                data.entryTimes.computeIfAbsent(item.getSecurityCode(), code -> new HashMap<>())
                        .put(item.getTargetPoolId(), new Date());
            } else if (pools.remove(item.getTargetPoolId())) {
                data.counts.compute(item.getTargetPoolId(), (id, count) -> Math.max(0, (count == null ? 0 : count) - 1));
            }
        }
    }

    /**
     * 将自动任务的调库项标记为校验失败
     *
     * 执行顺序：
     * 1. 将该调库项标记为不可调整，并用本次原因作为唯一失败原因
     *
     * @param item 当前主项
     * @param reason 本次校验产生的原因或提示
     */
    private void rejectScheduledItem(AdjustCheckDto.CheckResultItem item, String reason) {
        item.setCanAdjust(false);
        item.setFailReasons(Collections.singletonList(reason));
    }

    /**
     * 判断池是否要求研究报告
     *
     * 执行顺序：
     * 1. 检查文本
     *
     * @param restriction 限制
     * @return true=目标池配置了研究报告限制，false=目标池未配置研究报告限制
     */
    private boolean requiresReport(String restriction) {
        return StringUtils.hasText(restriction) && !"none".equals(restriction);
    }

    /**
     * 批量补齐 Excel 当前页展示的证券主档及候选
     *
     * 执行顺序：
     * 1. 创建本页独立的校验数据对象，读取证券主档及本页涉及的评级主体候选
     *
     * @param codes 当前页已去重的证券代码
     * @param selfSelected 当前页已去重的自选主体代码
     * @return 当前页展示所需的证券和主体信息
     */
    BatchSecurityCheckData loadExcelRatingCandidates(List<String> codes, List<String> selfSelected) {
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        completeExcelRatingCandidates(data, codes, selfSelected);
        return data;
    }

    /**
     * 补齐本次 Excel 首屏未查过的证券主档与评级候选
     *
     * 执行顺序：
     * 1. 先按输入补齐主档，再用主档中的实际代码查询评级候选
     * 2. 已查过但未命中的证券或候选同样复用空结果
     *
     * @param data 本次已读取的证券主档、关联主体及自选权益人
     * @param codes 当前页证券代码
     * @param selfSelected 当前页自选权益人代码
     */
    void completeExcelRatingCandidates(BatchSecurityCheckData data, List<String> codes, List<String> selfSelected) {
        List<String> missingMasters = codes.stream()
                .filter(code -> !data.lookedUpExcelSecurities.contains(code)).collect(Collectors.toList());
        // 1. 先按输入补齐主档，再用主档中的实际代码查询评级候选
        loadExcelSecurityMasters(missingMasters, data);
        List<String> masterCodes = codes.stream().map(data::resolveExcelSecurityCode).distinct()
                .filter(data.securities::containsKey).filter(code -> !data.lookedUpRatingCompanies.contains(code))
                .collect(Collectors.toList());
        List<String> missingSelfSelected = selfSelected.stream()
                .filter(code -> !data.lookedUpSelfSelectedCompanies.contains(code)).collect(Collectors.toList());
        // 2. 已查过但未命中的证券或候选同样复用空结果
        loadRatingCandidates(data, masterCodes, missingSelfSelected);
    }

    /**
     * 批量加载 Excel 的校验事实，供各行核对自己的评级主体
     *
     * 执行顺序：
     * 1. Excel 共用批量事实，清空场景另加载已占用的自选权益人
     *
     * @param requests 已通过入口校验的证券请求
     * @param clearTarget 是否为清空出库，清空使用已占用的 ABS 主体
     * @return 仅本批复用的校验数据
     */
    BatchSecurityCheckData loadExcelCheckData(List<AdjustCheckReq> requests, boolean clearTarget) {
        if (requests.isEmpty()) {
            return new BatchSecurityCheckData();
        }
        // Excel 共用批量事实，清空场景另加载已占用的自选权益人
        return loadBatchCheckData(requests, null, clearTarget, true);
    }

    /**
     * 执行 Excel 单行规则判定，使用整批已经读取的数据
     *
     * 执行顺序：
     * 1. 使用本批数据检查指定证券，适用普通调库阻断规则
     *
     * @param request 本行目标池、方向及主体选择
     * @param data 本批已经加载的校验事实
     * @return 含原业务提示的逐项校验结果
     */
    AdjustCheckDto checkExcelItem(AdjustCheckReq request, BatchSecurityCheckData data) {
        // Excel 继续自行注入批量流程推荐
        return securityPoolCheckRules.checkBatchItem(request, false, data, true);
    }

    /**
     * 执行严格的单笔出库校验并复用本批已查数据
     *
     * 执行顺序：
     * 1. 使用本批数据检查指定证券，适用普通调库阻断规则
     *
     * @param request 本行目标池、方向及主体选择
     * @param data 本批当前状态
     * @return 调库校验结果及流程候选
     */
    AdjustCheckDto checkClearItem(AdjustCheckReq request, BatchSecurityCheckData data) {
        return securityPoolCheckRules.checkBatchItem(request, false, data);
    }

    /**
     * 加载批次使用的证券、投资池及校验事实
     *
     * 执行顺序：
     * 1. 按池 ID 和关系类型索引本批查询到的关系池
     * 2. 先展开关联码，再读取主档、市场和在途状态
     * 3. 证券各自的校验事实由同一批次共享
     * 4. 主券与关联券都可能存在已确认或在途的 ABS 主体
     * 5. 加载关联评级主体及自选权益人
     * 6. 生效池状态也用于名单和调出冻结期判断
     * 7. 按涉及的目标池补充评级和流程资格
     *
     * @param requests 本批调库请求
     * @param maturityCutoff 普通债跟随任务的摘牌日截止日期，人工批量为空
     * @param clearTarget 是否加载清空出库已占用的自选权益人
     * @param excel 是否按 Excel 输入匹配主档代码
     * @return 本批共享校验数据
     */
    private BatchSecurityCheckData loadBatchCheckData(List<AdjustCheckReq> requests, String maturityCutoff,
                                                       boolean clearTarget, boolean excel) {
        boolean scheduled = maturityCutoff != null;
        BatchSecurityCheckData data = new BatchSecurityCheckData();
        data.scheduled = scheduled;
        List<PoolRelationBo> relations = batchSecurityCheckMapper.queryPoolRelations();
        // 1. 按池 ID 和关系类型索引本批查询到的关系池
        if (relations != null) {
            for (PoolRelationBo relation : relations) {
                data.relations.computeIfAbsent(relation.getPoolId(), id -> new HashMap<>())
                        .computeIfAbsent(relation.getRelationType(), type -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            }
        }
        Set<Long> targets = requests.stream().flatMap(req -> req.getItems().stream())
                .map(AdjustCheckReq.CheckItem::getTargetPoolId).collect(Collectors.toCollection(TreeSet::new));
        Set<Long> affectedPools = new TreeSet<>(targets);
        for (Long target : targets) {
            data.relations.getOrDefault(target, Collections.emptyMap()).values().forEach(affectedPools::addAll);
            if (scheduled) {
                affectedPools.addAll(AutoAdjustRelationHelper.resolveInboundAutoOutPoolIds(target, relations));
            }
        }
        investmentPoolMapper.queryPoolList().forEach(pool -> data.pools.put(pool.getId(), pool));
        batchSecurityCheckMapper.queryPoolCounts(new ArrayList<>(affectedPools))
                .forEach(pool -> data.counts.put(pool.getId(), pool.getCurrentCount()));
        List<Long> openDayPools = affectedPools.stream().filter(id -> data.pools.containsKey(id)
                && Integer.valueOf(1).equals(data.pools.get(id).getOpenDayAdjust())).collect(Collectors.toList());
        if (!openDayPools.isEmpty()) {
            data.openPools.addAll(batchSecurityCheckMapper.queryOpenPools(openDayPools, LocalDate.now().toString()));
        }
        batchSecurityCheckMapper.queryCategories()
                .forEach(row -> data.categories.putIfAbsent(row.getSecurityType(), row.getCategoryType()));

        if (excel) {
            List<String> inputCodes = requests.stream().map(AdjustCheckReq::getSecurityCode)
                    .distinct().collect(Collectors.toList());
            // 先按原输入查询主档，再用实际代码关联市场、池状态和同券信息
            loadExcelSecurityMasters(inputCodes, data);
            for (AdjustCheckReq request : requests) {
                request.setSecurityCode(data.resolveExcelSecurityCode(request.getSecurityCode()));
            }
        }
        List<String> mainCodes = requests.stream().map(AdjustCheckReq::getSecurityCode)
                .distinct().collect(Collectors.toList());
        Set<String> codes = new LinkedHashSet<>(mainCodes);
        // 2. 先展开关联码，再读取主档、市场和在途状态
        forEachCheckChunk(mainCodes, chunk -> {
            for (Row row : batchSecurityCheckMapper.queryRelatedCodes(chunk, MarketCode.queryCodeList())) {
                data.relatedCodes.computeIfAbsent(row.getSecurityCode(), key -> new ArrayList<>()).add(row.getRelatedCode());
                codes.add(row.getRelatedCode());
            }
        });
        List<String> actualCodes = new ArrayList<>(codes);
        Set<String> masterCodesToLoad = new LinkedHashSet<>(codes);
        if (excel) {
            // Excel 主券已读过主档，此处只补充新增的关联券
            masterCodesToLoad.removeAll(mainCodes);
        }
        if (scheduled) {
            // 以初筛使用的截止日复核本批实际调入代码
            forEachCheckChunk(new ArrayList<>(masterCodesToLoad), chunk -> {
                for (ScheduledSecurity security : batchSecurityCheckMapper.queryScheduledSecurities(chunk)) {
                    String code = security.getWindCode();
                    data.securities.put(code, security);
                    if (security.isEligibleForFollow()) {
                        data.scheduledEligibleWithoutMaturityCodes.add(code);
                        if (CompanyBondSyncPolicy.meetsDelistCutoff(security.getDelistDate(), maturityCutoff)) {
                            data.scheduledEligibleCodes.add(code);
                        }
                    }
                }
            });
            // 联动和关联券同样受发行主体最近一次 Excel 调出决定约束
            List<String> issuers = data.securities.values().stream().map(SecurityInfoBo::getIssuerCode)
                    .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
            // 使用现有自动调库的分批大小执行批次查询
            forEachCheckChunk(issuers, chunk -> {
                ExcelPoolExclusionService.CompanyDecisions decisions = excelPoolExclusionService.loadCompanyDecisions(null, chunk);
                for (String issuer : chunk) {
                    Set<Long> outPools = decisions.outPools(issuer);
                    if (!outPools.isEmpty()) {
                        data.excelCompanyOutPools.put(issuer, outPools);
                    }
                }
            });
        } else {
            // 使用现有自动调库的分批大小执行批次查询
            forEachCheckChunk(new ArrayList<>(masterCodesToLoad), chunk ->
                    batchSecurityCheckMapper.querySecurities(chunk)
                            .forEach(sec -> data.securities.put(sec.getWindCode(), sec)));
        }
        // 3. 证券各自的校验事实由同一批次共享
        forEachCheckChunk(actualCodes, chunk -> {
            securityMarketMapper.queryMarketMappingList(chunk).forEach(row -> data.markets
                    .computeIfAbsent(row.getWindCode(), key -> new ArrayList<>()).add(row));
            batchSecurityCheckMapper.queryPendingPools(chunk).forEach(row -> data.pendingPools
                    .computeIfAbsent(row.getSecurityCode(), key -> new HashSet<>()).add(row.getPoolId()));
            batchSecurityCheckMapper.queryPendingLabels(chunk).forEach(row ->
                    data.pendingLabels.put(row.getSecurityCode(), row.getNodeLabel()));
        });
        Set<String> absCodes = requests.stream().map(AdjustCheckReq::getSecurityCode)
                .filter(code -> CreditBondSpecialInboundRule.isAbs(data.securities.get(code)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> absAndRelated = new LinkedHashSet<>(absCodes);
        for (String code : absCodes) {
            absAndRelated.addAll(data.relatedCodes.getOrDefault(code, Collections.emptyList()));
        }
        // 4. 主券与关联券都可能存在已确认或在途的 ABS 主体
        forEachCheckChunk(new ArrayList<>(absAndRelated), chunk -> batchSecurityCheckMapper.queryAbsIdentities(chunk)
                .forEach(row -> data.absIdentities.computeIfAbsent(row.getSecurityCode(), key -> new ArrayList<>())
                        .add(row)));
        Set<String> selectionCodes = requests.stream().filter(req -> req.getSelectedRatingCompany() == null)
                .map(AdjustCheckReq::getSecurityCode).collect(Collectors.toCollection(LinkedHashSet::new));
        selectionCodes.addAll(absAndRelated);
        Set<String> selfSelected = requests.stream().filter(req -> absCodes.contains(req.getSecurityCode())
                        && req.getSelectedRatingCompany() == null)
                .map(AdjustCheckReq::getSelfSelectedRightsHolderCode).filter(StringUtils::hasText)
                .map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
        if (clearTarget) {
            for (String code : absCodes) {
                data.absIdentities.getOrDefault(code, Collections.emptyList()).stream().map(Row::getCompanyCode)
                        .filter(StringUtils::hasText).forEach(selfSelected::add);
            }
        }
        // 5. 加载关联评级主体及自选权益人
        loadRatingCandidates(data, new ArrayList<>(selectionCodes), new ArrayList<>(selfSelected));
        List<String> issuers = data.securities.values().stream().map(SecurityInfoBo::getIssuerCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Set<String> companies = new LinkedHashSet<>(issuers);
        for (String code : absAndRelated) {
            data.ratingCompanies.getOrDefault(code, Collections.emptyList()).stream().map(row -> row.getCompanyCode())
                    .filter(StringUtils::hasText).forEach(companies::add);
        }
        companies.addAll(selfSelected);
        requests.stream().filter(req -> absCodes.contains(req.getSecurityCode()) && req.getSelectedRatingCompany() != null)
                .map(req -> req.getSelectedRatingCompany().getCompanyCode()).filter(StringUtils::hasText)
                .forEach(companies::add);
        codes.addAll(companies);
        // 6. 生效池状态也用于名单和调出冻结期判断
        forEachCheckChunk(new ArrayList<>(codes), chunk -> {
            for (Row row : batchSecurityCheckMapper.queryActiveStates(chunk)) {
                data.activePools.computeIfAbsent(row.getSecurityCode(), key -> new HashSet<>()).add(row.getPoolId());
                if (row.getEntryTime() != null) {
                    data.entryTimes.computeIfAbsent(row.getSecurityCode(), key -> new HashMap<>())
                            .putIfAbsent(row.getPoolId(), row.getEntryTime());
                }
                if ("company".equals(row.getSecurityType())) {
                    data.blacklistCompanyPools.computeIfAbsent(row.getSecurityCode(), key -> new HashSet<>())
                            .add(row.getPoolId());
                }
            }
        });
        // 7. 按涉及的目标池补充评级和流程资格
        loadBatchRatingAndFlowData(data, requests, actualCodes, issuers, new ArrayList<>(companies), affectedPools);
        return data;
    }

    /**
     * 按本批目标池加载会用到的评级、流程和材料资格
     *
     * 执行顺序：
     * 1. 按调库方向和目标池判断本批要加载的校验数据
     * 2. 涉及信用债分级池调入或黑名单质押库调整时，读取启用的外部评级机构，有机构配置时为黑名单加载低外评主体
     * 3. 有信用债分级池调入时，读取期限区间和评级档，按机构配置查评级下调，并加载发行人历史入库、报告及分析资格
     * 4. 定时任务涉及报告限制时加载发行主体的报告豁免信息
     * 5. 产品池调入且能找到 CRMW 池时加载标的证券的有效 CRMW 组合来源
     *
     * @param data 本批共享数据
     * @param requests 本批调库请求
     * @param codes 扩展后的实际证券代码
     * @param issuers 证券主档发行主体代码
     * @param blacklistCompanies 黑名单质押库校验涉及的全部风险主体
     * @param poolIds 本批涉及的投资池
     */
    private void loadBatchRatingAndFlowData(BatchSecurityCheckData data, List<AdjustCheckReq> requests,
                                           List<String> codes, List<String> issuers,
                                           List<String> blacklistCompanies, Set<Long> poolIds) {
        // 1. 按调库方向和目标池判断本批要加载的校验数据
        boolean inbound = requests.stream().flatMap(req -> req.getItems().stream())
                .anyMatch(item -> AdjustMode.IN.getCode().equals(item.getAdjustMode()));
        List<InvestmentPoolBo> pools = poolIds.stream().map(data.pools::get).filter(Objects::nonNull)
                .collect(Collectors.toList());
        boolean creditBond = inbound && pools.stream()
                .anyMatch(pool -> CreditBondSpecialInboundRule.isGradedBondPoolType(pool.getPoolType()));
        boolean blacklist = poolIds.contains(PledgeBlacklistRuleService.BLACKLIST_POOL_ID);
        // 2. 涉及信用债分级池调入或黑名单质押库调整时，读取启用的外部评级机构，有机构配置时为黑名单加载低外评主体
        if (creditBond || blacklist) {
            List<String> agencies = externalRatingAgencyService.queryActiveAgencyCodeList();
            data.ratingAgenciesConfigured = !agencies.isEmpty();
            if (blacklist && data.ratingAgenciesConfigured) {
                // 低外评和两项名单条件分别计算
                forEachCheckChunk(blacklistCompanies, chunk -> data.lowRatedCompanies.addAll(
                        batchSecurityCheckMapper.queryLowRatedCompanies(chunk, agencies)));
            }
            // 3. 有信用债分级池调入时，读取期限区间和评级档，按机构配置查评级下调，并加载发行人历史入库、报告及分析资格
            if (creditBond) {
                data.termBuckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
                data.ratingGrades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
                Set<String> companies = new LinkedHashSet<>(issuers);
                requests.stream().map(AdjustCheckReq::getGuarantorCode).filter(StringUtils::hasText)
                        .forEach(companies::add);
                if (!agencies.isEmpty()) {
                    // 复用评级下调口径并沿用外部查询失败时的原有行为
                    forEachCheckChunk(new ArrayList<>(companies), chunk -> {
                        try {
                            for (WindIssuerRatingBo rating : batchSecurityCheckMapper.queryLatestRatings(chunk, agencies)) {
                                if (RatingComparator.isDowngraded(rating.getCreditRating(), rating.getPreCreditRating(),
                                        rating.getCreditRatingChange())) {
                                    data.downgradedCompanies.add(rating.getCompanyCode());
                                }
                            }
                        } catch (DataAccessException e) {
                            log.warn("批量查询 Wind 发行人评级失败：{}", e.getMessage());
                        }
                    });
                }
                // 按发行人查询独立的历史资格
                forEachCheckChunk(issuers, chunk -> {
                    data.nonSimpleInboundIssuers.addAll(batchSecurityCheckMapper.queryNonSimpleInboundIssuers(
                            chunk, SIMPLE_INBOUND_HISTORY_DAYS));
                    if (BusinessSwitches.SIMPLE_INBOUND_RECENT_REPORT_REQUIRED) {
                        data.recentReportIssuers.addAll(batchSecurityCheckMapper.queryRecentReportIssuers(
                                chunk, SIMPLE_INBOUND_HISTORY_DAYS));
                    }
                    data.recentAnalysisIssuers.addAll(batchSecurityCheckMapper.queryRecentAnalysisIssuers(
                            chunk, SIMPLE_INBOUND_HISTORY_DAYS));
                });
            }
        }
        // 4. 定时任务涉及报告限制时加载发行主体的报告豁免信息
        if (data.scheduled && pools.stream().anyMatch(pool -> requiresReport(pool.getInReportRestriction())
                // 判断池是否要求研究报告
                || requiresReport(pool.getOutReportRestriction()))) {
            // 自动入池无法上传文件，只使用已有业务豁免
            forEachCheckChunk(issuers, chunk -> data.reportExemptIssuers.addAll(
                    batchSecurityCheckMapper.queryReportExemptIssuers(chunk)));
        }
        // 5. 产品池调入且能找到 CRMW 池时加载标的证券的有效 CRMW 组合来源
        if (inbound && pools.stream().anyMatch(pool -> securityPoolCheckRules.isProductInboundTarget(pool, data.pools))) {
            data.pools.values().stream().filter(pool -> ProductInboundPoolCodes.CRMW.equals(pool.getPoolCode())
                    && Integer.valueOf(0).equals(pool.getIsDeleted())).findFirst().ifPresent(pool -> {
                // 产品库快速资格可使用有效 CRMW 组合来源
                forEachCheckChunk(codes, chunk -> data.crmwSources.addAll(
                        batchSecurityCheckMapper.queryCrmwSources(chunk, pool.getId())));
            });
        }
    }

    /**
     * 批量读取 Excel 证券主档并建立忽略大小写的代码索引
     *
     * 执行顺序：
     * 1. 查询证券
     *
     * @param codes 当前页已去重的证券代码
     * @param data 本批当前状态
     */
    private void loadExcelSecurityMasters(List<String> codes, BatchSecurityCheckData data) {
        // 使用现有自动调库的分批大小执行批次查询
        forEachCheckChunk(codes, chunk -> {
            for (SecurityInfoBo security : batchSecurityCheckMapper.querySecurities(chunk)) {
                String masterCode = security.getWindCode();
                data.securities.put(masterCode, security);
                data.excelSecurityCodes.put(masterCode, masterCode);
            }
            data.lookedUpExcelSecurities.addAll(chunk);
        });
    }

    /**
     * 批量读取未指定主体的 ABS 默认权益人
     *
     * 执行顺序：
     * 1. 按原候选顺序保留首项
     *
     * @param codes 本次提交未显式选择主体的证券代码
     * @return 各证券的首个关联主体编码
     */
    Map<String, String> loadDefaultRightsHolders(Set<String> codes) {
        Map<String, String> defaults = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // 按原候选顺序保留首项
        forEachCheckChunk(new ArrayList<>(codes), chunk -> batchSecurityCheckMapper.queryRatingCompanies(chunk)
                .forEach(row -> {
                    if (!defaults.containsKey(row.getSecurityCode())) {
                        defaults.put(row.getSecurityCode(), row.getCompanyCode());
                    }
                }));
        return defaults;
    }

    /**
     * 判断 ABS 提交项是否需要读取默认权益人
     *
     * 执行顺序：
     * 1. 仅当证券是 ABS 且权益人和自选权益人都未填写时，才需要读取默认值
     *
     * @param securityType 页面已知证券类型
     * @param rightsHolder 权益人
     * @param selfSelected 页面为当前 ABS 选填的自选权益人代码
     * @return true=该 ABS 尚未选择权益人，需要查询默认权益人，false=无需查询默认权益人
     */
    static boolean needsDefaultRightsHolder(String securityType, String rightsHolder, String selfSelected) {
        return "abs".equals(securityType) && !StringUtils.hasText(rightsHolder) && !StringUtils.hasText(selfSelected);
    }

    /**
     * 按代码分批加载关联评级主体和精确匹配的自选权益人
     *
     * 执行顺序：
     * 1. 查询评级主体
     *
     * @param data 本批当前状态
     * @param codes 当前页已去重的证券代码
     * @param selfSelected 当前页已去重的自选主体代码
     */
    void loadRatingCandidates(BatchSecurityCheckData data, List<String> codes, List<String> selfSelected) {
        // 使用现有自动调库的分批大小执行批次查询
        forEachCheckChunk(codes, chunk -> {
            batchSecurityCheckMapper.queryRatingCompanies(chunk)
                    .forEach(row -> data.ratingCompanies.computeIfAbsent(row.getSecurityCode(), key -> new ArrayList<>())
                            .add(row));
            data.lookedUpRatingCompanies.addAll(chunk);
        });
        // 使用现有自动调库的分批大小执行批次查询
        forEachCheckChunk(selfSelected, chunk -> {
            batchSecurityCheckMapper.querySelfSelectedCompanies(chunk)
                    .forEach(row -> data.selfSelectedCompanies.put(row.getCompanyCode(), row));
            data.lookedUpSelfSelectedCompanies.addAll(chunk);
        });
    }

    /**
     * 使用现有自动调库的分批大小执行批次查询
     *
     * 执行顺序：
     * 1. 按自动调库的每批代码数切分列表，依次查询每批，尾批使用剩余的代码
     *
     * @param codes 当前页已去重的证券代码
     * @param query 接收分批数据并执行操作的函数
     */
    private void forEachCheckChunk(List<String> codes, Consumer<List<String>> query) {
        int size = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        for (int offset = 0; offset < codes.size(); offset += size) {
            query.accept(codes.subList(offset, Math.min(offset + size, codes.size())));
        }
    }
}

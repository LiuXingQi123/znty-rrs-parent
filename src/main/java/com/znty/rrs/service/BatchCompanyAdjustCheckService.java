package com.znty.rrs.service;

import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData;
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData.CompanyState;
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData.Row;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import com.znty.rrs.mapper.BatchCompanyAdjustCheckMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理 Excel 主体调库校验的数据准备
 *
 * 证券池 Excel 导入页面校验主体调库或清空目标池时，通过 {@link SecurityPoolExcelImportService} 调用本类
 * 一次读取本批主体及相关池状态、投资池关系和所需评级，为 {@link ForbiddenPoolAdjustService} 逐项校验准备数据
 */
@Service
public class BatchCompanyAdjustCheckService {

    /** 每批读取主体校验数据的最大条数 */
    private static final int QUERY_SIZE = 500;

    /** 批量读取主体调库校验所需的数据 */
    @Resource
    private BatchCompanyAdjustCheckMapper batchCompanyAdjustCheckMapper;

    /** 读取禁投池状态和投资池关系 */
    @Resource
    private ForbiddenPoolAdjustMapper forbiddenPoolAdjustMapper;

    /** 读取本批校验涉及的投资池配置 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 取得本批认可的外部评级机构编码 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /**
     * 读取主体清空候选的主档，再加载调出校验数据
     *
     * 执行顺序：
     * 1. 逐项查询主体明细
     * 2. 独立清空按普通出库规则读取外评
     *
     * @param codes 清空差集中的主体代码
     * @param poolIds 清空主体所在目标池
     * @return 本次清空校验所需数据
     */
    public CompanyAdjustCheckContext loadClear(List<String> codes, Set<Long> poolIds) {
        Map<String, ForbiddenPoolAdjustDto> companies = new HashMap<>();
        for (int start = 0; start < codes.size(); start += QUERY_SIZE) {
            List<String> chunk = codes.subList(start, Math.min(start + QUERY_SIZE, codes.size()));
            for (CompanyImportMatchDto row : batchCompanyAdjustCheckMapper.queryCompanyDetails(chunk)) {
                ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
                company.setCompanyCode(row.getCompanyCode());
                company.setCompanyFullName(row.getCompanyFullName());
                company.setCompanyShortName(row.getCompanyShortName());
                companies.putIfAbsent(row.getCompanyCode(), company);
                companies.putIfAbsent(row.getLookupValue(), company);
            }
        }
        // 独立清空按普通出库规则读取外评
        return load(companies, poolIds, false, true);
    }

    /**
     * 按本次输入的主体和池范围批量读取 Excel 校验数据
     *
     * 执行顺序：
     * 1. 读取池配置与关系，确定手工及可能生成的联动互斥池
     * 2. 读取池容量、开放日和需要的评级机构配置
     * 3. 分批读取有效在池状态、在途节点、占用池及低外评
     *
     * @param companies 本次识别成功并有待校验行的主体
     * @param poolIds 手工目标池
     * @param inbound 是否为调入
     * @return 本次请求共享的主体校验数据
     */
    public CompanyAdjustCheckContext load(Map<String, ForbiddenPoolAdjustDto> companies, Set<Long> poolIds,
                                          boolean inbound) {
        // Excel 调出按配置联动，不需要取外评判断能否调出黑名单质押库
        return load(companies, poolIds, inbound, inbound);
    }

    /**
     * 批量读取本次规则需要的主体状态和池配置
     *
     * 执行顺序：
     * 1. 没有待校验主体时返回仅含空池和空关系数据的校验上下文
     * 2. 读取目标池和池关系，整理本次校验涉及的池
     * 3. 查询当前在池数量及开放日，准备冻结期和黑名单外评校验所需的数据
     * 4. 按主体代码分批读取池状态等校验数据并组装上下文
     *
     * @param companies 本次识别成功并有待校验行的主体
     * @param poolIds 清空主体所在目标池
     * @param inbound 是否为调入
     * @param checkBlacklistRating 是否校验黑名单评级
     * @return 本次请求共享的主体校验数据
     */
    private CompanyAdjustCheckContext load(Map<String, ForbiddenPoolAdjustDto> companies, Set<Long> poolIds,
                                           boolean inbound, boolean checkBlacklistRating) {
        BatchCompanyAdjustCheckData data = new BatchCompanyAdjustCheckData();
        // 1. 没有待校验主体时返回仅含空池和空关系数据的校验上下文
        if (companies.isEmpty()) {
            return new CompanyAdjustCheckContext(companies, Collections.emptyList(), Collections.emptyList(), data);
        }
        // 2. 读取目标池和池关系，整理本次校验涉及的池
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        List<PoolRelationBo> relations = forbiddenPoolAdjustMapper.queryAllPoolRelationList();
        for (InvestmentPoolBo pool : pools) {
            data.pools.put(pool.getId(), pool);
        }
        Set<Long> relevantPools = new HashSet<>(poolIds);
        Set<Long> outboundPools = inbound ? new HashSet<>() : new HashSet<>(poolIds);
        for (PoolRelationBo relation : relations) {
            data.relations.computeIfAbsent(relation.getPoolId(), key -> new HashMap<>())
                    .computeIfAbsent(relation.getRelationType(), key -> new ArrayList<>()).add(relation.getRelationPoolId());
            if (!poolIds.contains(relation.getPoolId()) || relation.getRelationPoolId() == null) {
                continue;
            }
            String type = relation.getRelationType();
            if (inbound && RelationType.IN_LINKED.getCode().equals(type)) {
                relevantPools.add(relation.getRelationPoolId());
            } else if ((inbound && RelationType.IN_MUTEX.getCode().equals(type))
                    || (!inbound && RelationType.OUT_LINKED.getCode().equals(type))) {
                relevantPools.add(relation.getRelationPoolId());
                outboundPools.add(relation.getRelationPoolId());
            }
        }
        // 3. 查询当前在池数量及开放日，准备冻结期和黑名单外评校验所需的数据
        for (Row row : batchCompanyAdjustCheckMapper.queryPoolCounts(relevantPools)) {
            data.capacities.put(row.getTargetPoolId(), row.getCurrentCount());
        }
        Set<Long> openDayPools = relevantPools.stream().map(data.pools::get)
                .filter(pool -> pool != null && Integer.valueOf(1).equals(pool.getOpenDayAdjust()))
                .map(InvestmentPoolBo::getId).collect(Collectors.toSet());
        if (!openDayPools.isEmpty()) {
            data.openToday.addAll(batchCompanyAdjustCheckMapper.queryOpenPools(openDayPools, LocalDate.now().toString()));
        }
        Set<Long> entryPools = outboundPools.stream().map(data.pools::get)
                .filter(pool -> pool != null && pool.getFrozenPeriodIn() != null && pool.getFrozenPeriodIn() > 0)
                .map(InvestmentPoolBo::getId).collect(Collectors.toSet());
        List<String> agencies = checkBlacklistRating && relevantPools.contains(PledgeBlacklistRuleService.BLACKLIST_POOL_ID)
                ? externalRatingAgencyService.queryActiveAgencyCodeList() : Collections.emptyList();
        data.ratingAgenciesConfigured = !agencies.isEmpty();
        data.categoryType = forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType("company");

        // 4. 按主体代码分批读取池状态等校验数据并组装上下文
        List<String> codes = companies.values().stream().map(ForbiddenPoolAdjustDto::getCompanyCode)
                .distinct().collect(Collectors.toList());
        for (String code : codes) {
            data.states.put(code, new CompanyState());
        }
        for (int start = 0; start < codes.size(); start += QUERY_SIZE) {
            List<String> chunk = codes.subList(start, Math.min(start + QUERY_SIZE, codes.size()));
            // 在池记录供在池、冻结期和池类型规则共用
            loadCompanyStates(data, chunk, entryPools, agencies);
        }
        return new CompanyAdjustCheckContext(companies, pools, relations, data);
    }

    /**
     * 加载一批主体的状态及外评
     *
     * 执行顺序：
     * 1. 逐项查询当前投资池
     * 2. 逐项查询在途投资池
     *
     * @param data 本次校验数据
     * @param codes 本次查询的主体代码
     * @param entryPools 需要冻结期数据的池
     * @param agencies 需要检查的外评机构
     */
    private void loadCompanyStates(BatchCompanyAdjustCheckData data, List<String> codes, Set<Long> entryPools,
                                   List<String> agencies) {
        for (Row row : batchCompanyAdjustCheckMapper.queryCurrentPools(codes, entryPools)) {
            CompanyState state = data.states.get(row.getSecurityCode());
            state.currentPoolIds.add(row.getTargetPoolId());
            if ("company".equals(row.getSecurityType())) {
                state.companyPoolIds.add(row.getTargetPoolId());
            }
            if (row.getEntryLogId() != null && !state.entryTimes.containsKey(row.getTargetPoolId())) {
                state.entryTimes.put(row.getTargetPoolId(), row.getEntryTime());
            }
        }
        for (Row row : batchCompanyAdjustCheckMapper.queryPendingLabels(codes)) {
            data.states.get(row.getSecurityCode()).pendingNodeLabel = row.getNodeLabel();
        }
        for (Row row : batchCompanyAdjustCheckMapper.queryPendingPools(codes)) {
            data.states.get(row.getSecurityCode()).pendingPoolIds.add(row.getTargetPoolId());
        }
        if (!agencies.isEmpty()) {
            for (String code : batchCompanyAdjustCheckMapper.queryLowRatedCompanies(codes, agencies)) {
                data.states.get(code).lowOuterRating = true;
            }
        }
    }
}

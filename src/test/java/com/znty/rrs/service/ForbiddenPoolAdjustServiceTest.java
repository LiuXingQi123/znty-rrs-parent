package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 禁投池主体调整服务测试。 */
public class ForbiddenPoolAdjustServiceTest {

    /** 验证主体详情通过一次汇总查询回填旗下债券数量。 */
    @Test
    public void queryCompanyDetailShouldFillCompanyBondCount() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ForbiddenPoolAdjustDto company = buildCompany("C10001");
        ForbiddenPoolAdjustDto.CompanyBondCount count = new ForbiddenPoolAdjustDto.CompanyBondCount();
        count.setCompanyCode("C10001");
        count.setBondCount(3);
        when(mapper.queryCompanyDetail("C10001")).thenReturn(company);
        when(mapper.queryCompanyBondCountList(Collections.singletonList("C10001")))
                .thenReturn(Collections.singletonList(count));

        ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
        req.setCompanyCode("C10001");

        ForbiddenPoolAdjustDto result = service.queryCompanyDetail(req);

        assertThat(result.getCompanyBondCount()).isEqualTo(3);
        verify(mapper).queryCompanyBondCountList(Collections.singletonList("C10001"));
    }

    /** 验证主体调库基础信息从主体表读取，不再查询主体证券记录。 */
    @Test
    public void queryAdjustSecurityInfoShouldReadCompanyFromCompanyTable() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C10001");
        company.setSecurityType("company");
        when(mapper.queryCompanySecurityBoByCode("C10001")).thenReturn(company);

        SecurityInfoBo result = ReflectionTestUtils.invokeMethod(service, "queryAdjustSecurityInfo", "C10001", "company");

        assertThat(result).isSameAs(company);
        verify(mapper).queryCompanySecurityBoByCode("C10001");
        verify(mapper, never()).querySecurityBoByCode("C10001");
    }

    /** 验证主体请求保持 company 类型，同时保留债券互斥项自己的调整对象。 */
    @Test
    public void convertCompanySubmitReqShouldUseFixedCompanySecurityType() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ForbiddenPoolAdjustSubmitReq req = new ForbiddenPoolAdjustSubmitReq();
        ForbiddenPoolAdjustSubmitReq.AdjustItem source = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
        source.setSecurityCode("B002");
        source.setSecurityShortName("测试债券");
        source.setSecurityType("company_bond");
        req.setItems(Collections.singletonList(source));
        ForbiddenPoolAdjustDto company = buildCompany("C10001");

        SecurityPoolAdjustSubmitReq result = ReflectionTestUtils.invokeMethod(
                service, "convertCompanySubmitReq", req, company);

        assertThat(result.getSecurityType()).isEqualTo("company");
        assertThat(result.getSecurityCode()).isEqualTo("C10001");
        assertThat(result.getItems().get(0).getSecurityCode()).isEqualTo("B002");
        assertThat(result.getItems().get(0).getSecurityShortName()).isEqualTo("测试债券");
        assertThat(result.getItems().get(0).getSecurityType()).isEqualTo("company_bond");
    }

    /** 验证调库日志优先使用调库项携带的债券调整对象。 */
    @Test
    public void buildAdjustLogShouldUseItemSecurity() {
        ForbiddenPoolAdjustService service = buildService(mock(ForbiddenPoolAdjustMapper.class));
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("C10001");
        req.setSecurityShortName("测试主体");
        req.setSecurityType("company");
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setSecurityCode("B002");
        item.setSecurityShortName("测试债券");
        item.setSecurityType("company_bond");
        item.setTargetPoolId(3L);
        item.setAdjustMode("调出");
        item.setItemTag("mutex");

        IpAdjustLogBo log = ReflectionTestUtils.invokeMethod(
                service, "buildAdjustLog", req, item, null, null);

        assertThat(log.getSecurityCode()).isEqualTo("B002");
        assertThat(log.getSecurityShortName()).isEqualTo("测试债券");
        assertThat(log.getSecurityType()).isEqualTo("company_bond");
        assertThat(log.getAdjustType()).isEqualTo("互斥调整");
    }

    /** 验证最终审批复核按债券代码校验互斥调出，不再误用主体代码。 */
    @Test
    public void recheckBeforeFinalApprovalShouldValidateMutexBond() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        InvestmentPoolBo pool = buildPool(3L, "二级库", "credit_bond");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.<PoolRelationBo>emptyList());
        SecurityInfoBo bond = buildBond("B002");
        bond.setSecurityType("company_bond");
        when(mapper.querySecurityBoByCode("B002")).thenReturn(bond);
        when(mapper.queryCategoryTypeBySecurityType("company_bond")).thenReturn("bond");
        when(mapper.querySecurityCurrentPoolIdList("B002")).thenReturn(Collections.singletonList(3L));

        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode("B002");
        log.setSecurityType("company_bond");
        log.setAdjustMode("调出");
        log.setTargetPoolId(3L);
        log.setTargetPoolName("二级库");

        service.recheckBeforeFinalApproval(Collections.singletonList(log));

        verify(mapper).querySecurityBoByCode("B002");
        verify(mapper, never()).queryCompanySecurityBoByCode("B002");
    }

    /** 验证手工调库目标池超出 15、16、17、23 时直接拒绝。 */
    @Test(expected = BizException.class)
    public void checkCompanyAdjustShouldRejectPoolOutsideAllowedRange() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCompanyDetail("C10001")).thenReturn(buildCompany("C10001"));
        when(mapper.queryCompanyBondCountList(Collections.singletonList("C10001")))
                .thenReturn(Collections.<ForbiddenPoolAdjustDto.CompanyBondCount>emptyList());
        ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
        item.setTargetPoolId(2L);
        item.setAdjustMode("调入");
        ForbiddenPoolAdjustCheckReq req = new ForbiddenPoolAdjustCheckReq();
        req.setCompanyCode("C10001");
        req.setItems(Collections.singletonList(item));

        service.checkCompanyAdjust(req);
    }

    /** 验证可调投资池仅返回禁投池、观察池、黑名单质押库和重点观察名单，并回填上限与现有数量。 */
    @Test
    public void queryCompanyAdjustPoolListShouldOnlyReturnConfiguredRiskPools() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        InvestmentPoolBo forbiddenPool = buildPool(15L, "债券禁止库", "forbidden");
        forbiddenPool.setMaxCapacity(1000L);
        when(poolMapper.queryPoolByIdsList(anyListOf(Long.class)))
                .thenReturn(Arrays.asList(
                        forbiddenPool,
                        buildPool(16L, "观察池", "observe"),
                        buildPool(17L, "黑名单质押库", "blacklist"),
                        buildPool(23L, "重点观察名单", "restricted")));
        when(poolMapper.queryMutexRelationList()).thenReturn(Collections.emptyList());
        PoolDto countDto = new PoolDto();
        countDto.setId(15L);
        countDto.setCurrentCount(12);
        when(mapper.queryPoolCurrentCountList()).thenReturn(Collections.singletonList(countDto));
        ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
        req.setCurrentUserId("1");

        List<PoolDto> result = service.queryCompanyAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsOnly(15L, 16L, 17L, 23L);
        PoolDto forbiddenDto = result.stream().filter(p -> Long.valueOf(15L).equals(p.getId())).findFirst().orElse(null);
        assertThat(forbiddenDto).isNotNull();
        assertThat(forbiddenDto.getMaxCapacity()).isEqualTo(1000L);
        assertThat(forbiddenDto.getCurrentCount()).isEqualTo(12);
    }

    /** 验证主体调入债券禁止库时，债券同步入池并从当前实际所在的关系池自动调出。 */
    @Test
    public void syncCompanyBondsOnDirectShouldInsertBondAndAutoOutRelatedPool() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        SecurityInfoBo newBond = buildBond("B002");
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(newBond));
        when(mapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));
        when(poolMapper.queryPoolByIdsList(anyListOf(Long.class)))
                .thenReturn(Collections.singletonList(buildPool(3L, "二级库", "credit_bond")));
        when(mapper.querySecurityCurrentPoolIdList("B002")).thenReturn(Arrays.asList(2L, 3L, 15L));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(mapper.deletePoolStatusSoft("B002", 3L)).thenReturn(1);
        doAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityShortName("某公司");
        companyLog.setSecurityType("company");
        companyLog.setAdjustMode("调入");
        companyLog.setAdjustBatchNo("COMPANY202606281001");
        companyLog.setTargetPoolId(15L);
        companyLog.setTargetPoolName("债券禁止库");
        companyLog.setPoolType("forbidden");
        companyLog.setAdjusterId("1");
        companyLog.setAdjusterName("管理员");

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(captor.capture());
        IpAdjustLogBo inboundLog = captor.getAllValues().get(0);
        IpAdjustLogBo outboundLog = captor.getAllValues().get(1);
        assertThat(inboundLog.getSecurityCode()).isEqualTo("B002");
        assertThat(inboundLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(inboundLog.getAdjustBatchNo()).isEqualTo(companyLog.getAdjustBatchNo());
        assertThat(outboundLog.getSecurityCode()).isEqualTo("B002");
        assertThat(outboundLog.getAdjustType()).isEqualTo("互斥调整");
        assertThat(outboundLog.getAdjustMode()).isEqualTo("调出");
        assertThat(outboundLog.getTargetPoolId()).isEqualTo(3L);
        assertThat(outboundLog.getTargetPoolName()).isEqualTo("二级库");
        assertThat(outboundLog.getPoolType()).isEqualTo("credit_bond");
        assertThat(outboundLog.getAdjustReason()).contains("调入债券禁止库", "自动调出“二级库”");
        verify(mapper).addPoolStatus(inboundLog);
        verify(mapper).deletePoolStatusSoft("B002", 3L);
        verify(mapper, never()).deletePoolStatusSoft("B002", 2L);
    }

    /** 验证同批已有债券互斥调出项时，主体同步不重复生成日志和删除池状态。 */
    @Test
    public void syncCompanyBondsShouldLeaveExplicitOutboundToBatchApply() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(buildBond("B002")));
        when(mapper.queryAllPoolRelationList()).thenReturn(
                Collections.singletonList(buildRelation(15L, "in_mutex", 3L)));
        when(poolMapper.queryPoolByIdsList(Collections.singletonList(3L)))
                .thenReturn(Collections.singletonList(buildPool(3L, "二级库", "credit_bond")));
        when(mapper.querySecurityCurrentPoolIdList("B002")).thenReturn(Arrays.asList(3L, 15L));
        when(mapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBonds", buildCompanyLog(),
                Collections.singleton("B002|3"));

        verify(mapper, times(1)).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper, never()).deletePoolStatusSoft("B002", 3L);
    }

    /** 验证调入互斥关系与反向调入限制关系会合并，重复池仅保留一次。 */
    @Test
    public void resolveInboundAutoOutPoolIdsShouldMergeAndDeduplicateRelations() {
        List<PoolRelationBo> relations = Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L),
                buildRelation(4L, "in_restrict", 15L),
                buildRelation(15L, "in_restrict", 5L));

        List<Long> poolIds = AutoAdjustRelationHelper.resolveInboundAutoOutPoolIds(15L, relations);

        assertThat(poolIds).containsExactly(3L, 4L);
    }

    /** 验证校验结果会追加以具体债券为调整对象的互斥调出项。 */
    @Test
    public void appendCompanyBondMutexOutItemsShouldAddBondRows() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(mapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));
        ForbiddenPoolAdjustDto.CompanyBond bond = new ForbiddenPoolAdjustDto.CompanyBond();
        bond.setWindCode("B002");
        bond.setShortName("测试债券");
        bond.setSecurityType("company_bond");
        bond.setTargetPoolId(3L);
        when(mapper.queryCompanyBondMutexOutList("C10001", 15L, Collections.singletonList(3L)))
                .thenReturn(Collections.singletonList(bond));
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, "信用债大库", "credit_bond"),
                buildChildPool(3L, 1L, "二级库", "credit_bond")));
        AdjustCheckDto.CheckResultItem manual = new AdjustCheckDto.CheckResultItem();
        manual.setTargetPoolId(15L);
        manual.setAdjustMode("调入");
        manual.setItemTag("manual");
        manual.setAdjustGroupKey("15_调入");
        manual.setCanAdjust(true);
        AdjustCheckDto result = new AdjustCheckDto();
        result.setItems(new java.util.ArrayList<>(Collections.singletonList(manual)));

        ReflectionTestUtils.invokeMethod(service, "appendCompanyBondMutexOutItems", "C10001", result);

        assertThat(result.getItems()).hasSize(2);
        AdjustCheckDto.CheckResultItem mutexItem = result.getItems().get(1);
        assertThat(mutexItem.getSecurityCode()).isEqualTo("B002");
        assertThat(mutexItem.getSecurityShortName()).isEqualTo("测试债券");
        assertThat(mutexItem.getSecurityType()).isEqualTo("company_bond");
        assertThat(mutexItem.getTargetPoolId()).isEqualTo(3L);
        assertThat(mutexItem.getPoolName()).isEqualTo("信用债大库/二级库");
        assertThat(mutexItem.getAdjustMode()).isEqualTo("调出");
        assertThat(mutexItem.getItemTag()).isEqualTo("mutex");
        assertThat(mutexItem.isCanAdjust()).isTrue();
        assertThat(mutexItem.getFlowOptions()).isEmpty();
    }

    /** 验证主体调入观察池等非债券禁止库时不同步旗下债券。 */
    @Test
    public void syncCompanyBondsShouldSkipWhenTargetIsNotBondForbiddenPool() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityType("company");
        companyLog.setAdjustMode("调入");
        companyLog.setTargetPoolId(16L);
        companyLog.setTargetPoolName("观察池");
        companyLog.setPoolType("observe");

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);

        verify(mapper, never()).queryCategoryTypeBySecurityType(any(String.class));
        verify(mapper, never()).queryCompanyInboundBondForAutoList(any(String.class), any(Long.class));
        verify(mapper, never()).queryCompanyOutboundBondForAutoList(any(String.class), any(Long.class));
        verify(mapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 验证同步债券调入写入数量异常时阻断整批事务。 */
    @Test(expected = BizException.class)
    public void syncCompanyBondsShouldFailWhenInboundInsertCountIsInvalid() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(buildBond("B002")));
        doAnswer(invocation -> {
            ((IpAdjustLogBo) invocation.getArguments()[0]).setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(0);

        IpAdjustLogBo companyLog = buildCompanyLog();
        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);
    }

    /** 验证主体调入不受证券品种和市场配置拦截。 */
    @Test
    public void checkInConditionsShouldSkipVarietyAndMarketForCompany() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolBo pool = buildPool(15L, "禁投池", "forbidden");
        pool.setVarietyCodes("[\"bond\"]");
        pool.setMarketCodes("[\"SSE\"]");
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C10001");
        company.setSecurityType("company");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(company);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("company");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        when(mapper.querySecurityInForbiddenPool("C10001")).thenReturn(false);

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
        verify(mapper, never()).queryCategoryTypeBySecurityType("company");
    }

    /** 禁投池链路报告必填校验：限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, "禁投池", "forbidden");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", null);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("any 限制且无报告时应抛出异常");
    }

    /** 验证内部报告限制会调用统一附件来源校验。 */
    @Test
    public void checkReportRequiredShouldValidateInternalReportSources() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(100L));
        InvestmentPoolBo pool = buildPool(15L, "禁投池", "forbidden");

        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", "C10001");

        verify(attachmentService).validateCreditReportSources(Collections.singletonList(100L), true);
    }

    /** 构建服务并注入公共依赖。 */
    private ForbiddenPoolAdjustService buildService(ForbiddenPoolAdjustMapper mapper) {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        return service;
    }

    /** 构建主体数据。 */
    private ForbiddenPoolAdjustDto buildCompany(String companyCode) {
        ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
        company.setCompanyCode(companyCode);
        company.setCompanyShortName("某公司");
        return company;
    }

    /**
     * 验证债券特殊策略类流程（发起人→多层 auto→结束）判为直通。
     * 旧逻辑只认「发起人后直接 end」，会误判为非直通导致联动漏落池。
     */
    @Test
    public void isDirectFlowShouldAcceptInitiatorThenAutoChain() throws Exception {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        FlowNodeBo start = buildFlowNode(1L, "start");
        FlowNodeBo initiator = buildFlowNode(2L, "approval");
        FlowNodeBo auto1 = buildFlowNode(3L, "approval");
        FlowNodeBo auto2 = buildFlowNode(4L, "approval");
        FlowNodeBo end = buildFlowNode(5L, "end");
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        nodeMap.put(1L, start);
        nodeMap.put(2L, initiator);
        nodeMap.put(3L, auto1);
        nodeMap.put(4L, auto2);
        nodeMap.put(5L, end);
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        configMap.put(2L, buildApprovalConfig(2L, "initiator"));
        configMap.put(3L, buildApprovalConfig(3L, "auto"));
        configMap.put(4L, buildApprovalConfig(4L, "auto"));
        List<FlowEdgeBo> edges = Arrays.asList(
                buildEdge(1L, 2L, "auto"),
                buildEdge(2L, 3L, "submit"),
                buildEdge(3L, 4L, "auto"),
                buildEdge(4L, 5L, "auto"));
        Object snapshot = buildInnerFlowSnapshot(nodeMap, edges, configMap);

        Boolean direct = ReflectionTestUtils.invokeMethod(service, "isDirectFlow", snapshot);

        assertThat(direct).isTrue();
    }

    /** 验证主路径存在人工 preempt 节点时仍为非直通。 */
    @Test
    public void isDirectFlowShouldRejectHumanPreemptNode() throws Exception {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        FlowNodeBo start = buildFlowNode(1L, "start");
        FlowNodeBo initiator = buildFlowNode(2L, "approval");
        FlowNodeBo human = buildFlowNode(3L, "approval");
        FlowNodeBo end = buildFlowNode(4L, "end");
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        nodeMap.put(1L, start);
        nodeMap.put(2L, initiator);
        nodeMap.put(3L, human);
        nodeMap.put(4L, end);
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        configMap.put(2L, buildApprovalConfig(2L, "initiator"));
        configMap.put(3L, buildApprovalConfig(3L, "preempt"));
        List<FlowEdgeBo> edges = Arrays.asList(
                buildEdge(1L, 2L, "auto"),
                buildEdge(2L, 3L, "submit"),
                buildEdge(3L, 4L, "approve"));
        Object snapshot = buildInnerFlowSnapshot(nodeMap, edges, configMap);

        Boolean direct = ReflectionTestUtils.invokeMethod(service, "isDirectFlow", snapshot);

        assertThat(direct).isFalse();
    }

    /** 构造 Service 内部 FlowSnapshot（与 entity.flow.FlowSnapshot 不同）。 */
    private Object buildInnerFlowSnapshot(Map<Long, FlowNodeBo> nodeMap, List<FlowEdgeBo> edges,
                                          Map<Long, NodeApprovalConfigBo> configMap) throws Exception {
        Class<?> snapshotClass = Class.forName(
                "com.znty.rrs.service.ForbiddenPoolAdjustService$FlowSnapshot");
        Constructor<?> ctor = snapshotClass.getDeclaredConstructor(
                FlowDefinitionBo.class, FlowVersionBo.class, Map.class, List.class, Map.class, Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(null, null, nodeMap, edges, configMap,
                Collections.<Long, List<NodeApprovalHandlerBo>>emptyMap());
    }

    private FlowNodeBo buildFlowNode(Long id, String nodeType) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setNodeType(nodeType);
        return node;
    }

    private NodeApprovalConfigBo buildApprovalConfig(Long nodeId, String strategy) {
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setNodeId(nodeId);
        config.setApprovalStrategy(strategy);
        return config;
    }

    private FlowEdgeBo buildEdge(Long from, Long to, String routeAction) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        edge.setRouteAction(routeAction);
        return edge;
    }

    /** 构建投资池数据。 */
    private InvestmentPoolBo buildPool(Long id, String poolName, String poolType) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName(poolName);
        pool.setPoolType(poolType);
        pool.setStatus("enabled");
        pool.setIsDeleted(0);
        return pool;
    }

    /** 构建子级投资池数据。 */
    private InvestmentPoolBo buildChildPool(Long id, Long parentId, String poolName, String poolType) {
        InvestmentPoolBo pool = buildPool(id, poolName, poolType);
        pool.setParentId(parentId);
        return pool;
    }

    /** 构建投资池关系数据。 */
    private PoolRelationBo buildRelation(Long poolId, String relationType, Long relationPoolId) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(poolId);
        relation.setRelationType(relationType);
        relation.setRelationPoolId(relationPoolId);
        return relation;
    }

    /** 构建债券数据。 */
    private SecurityInfoBo buildBond(String windCode) {
        SecurityInfoBo bond = new SecurityInfoBo();
        bond.setWindCode(windCode);
        bond.setShortName(windCode + "简称");
        bond.setSecurityType("company_bond");
        return bond;
    }

    /** 构建已审批通过的主体调入日志。 */
    private IpAdjustLogBo buildCompanyLog() {
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityShortName("某公司");
        companyLog.setSecurityType("company");
        companyLog.setAdjustMode("调入");
        companyLog.setAdjustBatchNo("COMPANY202606281001");
        companyLog.setTargetPoolId(15L);
        companyLog.setTargetPoolName("禁投池");
        companyLog.setPoolType("forbidden");
        companyLog.setAdjusterId("1");
        companyLog.setAdjusterName("管理员");
        return companyLog;
    }
}

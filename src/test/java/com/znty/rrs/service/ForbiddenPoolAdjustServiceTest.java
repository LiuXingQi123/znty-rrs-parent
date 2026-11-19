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
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

/** 禁投池主体调整服务测试。 */
public class ForbiddenPoolAdjustServiceTest {

    /** 批次内复用主体和公共配置，仍逐主体复核当前在池事实，单笔请求保持重新读取。 */
    @Test
    public void batchCompanyCheckShouldReuseMasterAndConfigurationButCheckLiveState() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        InvestmentPoolBo pool = buildPool(16L, "观察池", "observe");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        Map<String, ForbiddenPoolAdjustDto> companies = new HashMap<>();
        companies.put("C001", buildCompany("C001"));
        companies.put("C002", buildCompany("C002"));
        when(mapper.querySecurityCurrentPoolIdList("C001")).thenReturn(Collections.singletonList(16L));
        CompanyAdjustCheckContext context = service.createCompanyCheckContext(companies);
        for (String code : Arrays.asList("C001", "C002")) {
            ForbiddenPoolAdjustCheckReq req = new ForbiddenPoolAdjustCheckReq();
            req.setCompanyCode(code);
            ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
            item.setTargetPoolId(16L);
            item.setAdjustMode("调出");
            req.setItems(Collections.singletonList(item));
            AdjustCheckDto result = service.checkCompanyAdjust(req, context);
            assertThat(result.getItems().get(0).isCanAdjust()).isEqualTo("C001".equals(code));
            assertThat(result.getItems().get(0).getSecurityShortName()).isEqualTo("某公司有限公司");
            verify(mapper).querySecurityCurrentPoolIdList(code);
        }
        verify(poolMapper, times(1)).queryPoolList();
        verify(mapper, times(1)).queryAllPoolRelationList();
        verify(mapper, never()).queryCompanyDetail(any(String.class));
        verify(mapper, never()).queryCompanySecurityBoByCode(any(String.class));
        verify(mapper, never()).queryCompanyBondCountList(anyList());
        ForbiddenPoolAdjustCheckReq single = new ForbiddenPoolAdjustCheckReq();
        single.setCompanyCode("C001");
        assertThatThrownBy(() -> service.checkCompanyAdjust(single)).isInstanceOf(BizException.class)
                .hasMessageContaining("公司主体不存在");
        verify(mapper).queryCompanyDetail("C001");
    }

    /** 主体清空出库保留锁池和在途提示，并允许按调出流程提交和生效 */
    @Test
    public void excelOutboundShouldBlockPendingProcess() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ReportService reportService = mock(ReportService.class);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "reportService", reportService);
        InvestmentPoolBo pool = buildPool(16L, "观察池", "observe");
        pool.setLockFlag(1);
        pool.setOutReportRestriction("internal");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.queryCompanyDetail("C001")).thenReturn(buildCompany("C001"));
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C001");
        company.setSecurityType("company");
        when(mapper.queryCompanySecurityBoByCode("C001")).thenReturn(company);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.querySecurityCurrentPoolIdList("C001")).thenReturn(Collections.singletonList(16L));
        when(mapper.queryPendingManualTargetPoolIdList("C001", null)).thenReturn(Collections.singletonList(16L));
        when(mapper.deletePoolStatusSoft("C001", 16L)).thenReturn(1);

        ForbiddenPoolAdjustCheckReq check = new ForbiddenPoolAdjustCheckReq();
        check.setCompanyCode("C001");
        ForbiddenPoolAdjustCheckReq.CheckItem checkItem = new ForbiddenPoolAdjustCheckReq.CheckItem();
        checkItem.setTargetPoolId(16L);
        checkItem.setAdjustMode("调出");
        check.setItems(Collections.singletonList(checkItem));
        assertThat(service.checkCompanyAdjust(check).getItems().get(0).isCanAdjust()).isFalse();

        AdjustCheckDto.CheckResultItem cleared = service.checkCompanyExcelAdjust(check, null, true).getItems().get(0);
        assertThat(cleared.isCanAdjust()).isFalse();
        assertThat(cleared.getFailReasons()).contains("目标投资池已锁定");
        assertThat(cleared.getFailReasons()).anyMatch(reason -> reason.contains("流程"));
        assertThat(cleared.getFlowOptions()).isEmpty();
        verify(mapper, never()).deletePoolStatusSoft("C001", 16L);
    }

    /** 主体 Excel 调入重点观察名单时，按规则联动黑名单质押库，通过的联动项无额外提示 */
    @Test
    public void excelInboundShouldLinkBlacklistFromKeyWatchWithoutTip() {
        ForbiddenPoolAdjustService service = buildService(mock(ForbiddenPoolAdjustMapper.class));
        ReflectionTestUtils.setField(service, "pledgeBlacklistRuleService", new PledgeBlacklistRuleService());
        BatchCompanyAdjustCheckData batch = new BatchCompanyAdjustCheckData();
        batch.categoryType = "company";
        batch.ratingAgenciesConfigured = true;
        batch.states.put("C10003", new BatchCompanyAdjustCheckData.CompanyState());
        batch.pools.put(23L, buildPool(23L, "重点观察名单", "restricted"));
        batch.pools.put(17L, buildPool(17L, "黑名单质押库", "blacklist"));
        batch.relations.put(23L, Collections.singletonMap("in_linked", Collections.singletonList(17L)));
        CompanyAdjustCheckContext context = new CompanyAdjustCheckContext(
                Collections.singletonMap("C10003", buildCompany("C10003")),
                Collections.emptyList(), Collections.emptyList(), batch);

        ForbiddenPoolAdjustCheckReq request = new ForbiddenPoolAdjustCheckReq();
        request.setCompanyCode("C10003");
        ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
        item.setTargetPoolId(23L);
        item.setAdjustMode("调入");
        request.setItems(Collections.singletonList(item));

        List<AdjustCheckDto.CheckResultItem> items = service.checkCompanyExcelAdjust(request, context, true).getItems();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getTargetPoolId).containsExactly(23L, 17L);
        assertThat(items).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(items.get(1).getItemTag()).isEqualTo("linkage");
        assertThat(items.get(1).getFailReasons()).isEmpty();
        assertThat(items.get(1).getWarnings()).isNullOrEmpty();
        assertThat(service.checkCompanyAdjust(request, context).getItems().get(1).getWarnings()).isNullOrEmpty();
    }

    /** 主体 Excel 调出按已配置的联动池生成 17 池调出项，不复核黑名单质押库三条件 */
    @Test
    public void excelOutboundShouldExpandBlacklistLinkEvenWhenOrdinaryOutboundWouldRetainIt() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        PledgeBlacklistRuleService pledge = mock(PledgeBlacklistRuleService.class);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "pledgeBlacklistRuleService", pledge);
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(23L, "重点观察名单", "restricted"), buildPool(17L, "黑名单质押库", "blacklist")));
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(buildRelation(23L, "out_linked", 17L)));
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.querySecurityCurrentPoolIdList("C10007")).thenReturn(Arrays.asList(23L, 17L));
        when(pledge.evaluate("C10007", Collections.emptySet(), Collections.singleton(23L)))
                .thenReturn(new PledgeBlacklistRuleService.Decision(false, true, false));

        CompanyAdjustCheckContext context = service.createCompanyCheckContext(
                Collections.singletonMap("C10007", buildCompany("C10007")));
        ForbiddenPoolAdjustCheckReq request = new ForbiddenPoolAdjustCheckReq();
        request.setCompanyCode("C10007");
        ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
        item.setTargetPoolId(23L);
        item.setAdjustMode("调出");
        request.setItems(Collections.singletonList(item));

        assertThat(service.checkCompanyAdjust(request, context).getItems())
                .extracting(AdjustCheckDto.CheckResultItem::getTargetPoolId).containsExactly(23L);
        List<AdjustCheckDto.CheckResultItem> items = service.checkCompanyExcelAdjust(request, context, true).getItems();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getTargetPoolId).containsExactly(23L, 17L);
        assertThat(items).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(items.get(1).getItemTag()).isEqualTo("linkage");
        assertThat(items.get(1).getFailReasons()).isEmpty();
        assertThat(items.get(1).getAdjustGroupKey()).isEqualTo(items.get(0).getAdjustGroupKey());
        when(mapper.queryPendingManualTargetPoolIdList("C10007", null)).thenReturn(Collections.singletonList(17L));
        assertThat(service.checkCompanyExcelAdjust(request, context, true).getItems())
                .hasSize(2).noneMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(service.checkCompanyExcelAdjust(request, context, false).getItems())
                .hasSize(1).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        verify(pledge).evaluate("C10007", Collections.emptySet(), Collections.singleton(23L));
        verify(pledge, never()).validate(any(), any(), any(), any());
    }

    /** 主体清空及同批联动已出池时可重复生效，普通主体出库仍校验池状态 */
    @Test
    public void excelClearOutboundShouldAllowAlreadyRemovedLinkedMember() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        IpAdjustLogBo clear = buildCompanyLog();
        clear.setTargetPoolId(16L);
        clear.setAdjustMode("调出");
        clear.setAdjustType("清空目标池");
        clear.setAdjustBatchNo("COMP_CLEAR");
        IpAdjustLogBo linked = buildCompanyLog();
        linked.setTargetPoolId(17L);
        linked.setAdjustMode("调出");
        linked.setAdjustType("联动调整");
        linked.setAdjustBatchNo("COMP_CLEAR");

        service.applyPoolStatusChanges(Arrays.asList(clear, linked));

        verify(mapper).deletePoolStatusSoft("C10001", 16L);
        verify(mapper).deletePoolStatusSoft("C10001", 17L);
        assertThatThrownBy(() -> service.applyPoolStatusChanges(Collections.singletonList(linked)))
                .isInstanceOf(BizException.class).hasMessageContaining("当前池状态");
    }

    /** 已在目标池的主体导入在校验和实际落池时都拦截 */
    @Test
    public void excelInboundShouldBlockExistingPoolStatus() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
        InvestmentPoolBo pool = buildPool(16L, "观察池", "observe");
        pool.setInReportRestriction("internal");
        when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        when(mapper.queryCompanyDetail("C001")).thenReturn(buildCompany("C001"));
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C001");
        company.setSecurityType("company");
        when(mapper.queryCompanySecurityBoByCode("C001")).thenReturn(company);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.querySecurityCurrentPoolIdList("C001")).thenReturn(Collections.singletonList(16L));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

        ForbiddenPoolAdjustCheckReq check = new ForbiddenPoolAdjustCheckReq();
        check.setCompanyCode("C001");
        ForbiddenPoolAdjustCheckReq.CheckItem checkItem = new ForbiddenPoolAdjustCheckReq.CheckItem();
        checkItem.setTargetPoolId(16L);
        checkItem.setAdjustMode("调入");
        check.setItems(Collections.singletonList(checkItem));
        CompanyAdjustCheckContext context = service.createCompanyCheckContext(
                Collections.singletonMap("C001", buildCompany("C001")));
        assertThat(service.checkCompanyAdjust(check, context).getItems().get(0).isCanAdjust()).isFalse();
        AdjustCheckDto.CheckResultItem checked = service.checkCompanyExcelAdjust(check, context, true).getItems().get(0);
        assertThat(checked.isCanAdjust()).isFalse();
        assertThat(checked.getFailReasons()).anyMatch(reason -> reason.contains("已在"));
        assertThat(checked.getFlowOptions()).isEmpty();

        ForbiddenPoolAdjustSubmitReq req = new ForbiddenPoolAdjustSubmitReq();
        req.setCompanyCode("C001");
        req.setAdjusterId("1");
        req.setAdjustType("Excel导入");
        ForbiddenPoolAdjustSubmitReq.AdjustItem item = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(16L);
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey(checked.getAdjustGroupKey());
        req.setItems(Collections.singletonList(item));
        assertThatThrownBy(() -> service.addCompanyAdjustLog(req, Collections.emptyList()))
                .isInstanceOf(BizException.class).hasMessageContaining("Excel 调库");
        assertThatThrownBy(() -> service.addCompanyExcelAdjustLog(req))
                .isInstanceOf(BizException.class).hasMessageContaining("已在目标投资池");
        verify(mapper, never()).deletePoolStatusSoft("C001", 16L);
        verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 主体校验结果使用主体全称，失败及联动项均保留身份，债券互斥项不被覆盖。 */
    @Test
    public void companyCheckResultsShouldCarryMasterNameInBothDirections() {
        for (String direction : Arrays.asList("调入", "调出")) {
            for (boolean canAdjust : Arrays.asList(true, false)) {
                ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
                // 隔离内部复杂规则，仅验证主体边界对校验结果身份的补全
                ForbiddenPoolAdjustService service = spy(buildService(mapper));
                ForbiddenPoolAdjustDto company = buildCompany("C10001");
                when(mapper.queryCompanyDetail("C10001")).thenReturn(company);
                AdjustCheckDto.CheckResultItem manual = new AdjustCheckDto.CheckResultItem();
                manual.setItemTag("manual");
                manual.setTargetPoolId(15L);
                manual.setAdjustMode(direction);
                manual.setCanAdjust(canAdjust);
                manual.setFailReasons(canAdjust ? Collections.emptyList() : Collections.singletonList("已在目标池"));
                AdjustCheckDto.CheckResultItem linked = new AdjustCheckDto.CheckResultItem();
                linked.setItemTag("linkage");
                linked.setTargetPoolId(17L);
                linked.setAdjustMode(direction);
                AdjustCheckDto.CheckResultItem bond = new AdjustCheckDto.CheckResultItem();
                bond.setItemTag("mutex");
                bond.setSecurityCode("B001");
                bond.setSecurityShortName("24交投信用");
                bond.setSecurityType("company_bond");
                bond.setTargetPoolId(3L);
                bond.setAdjustMode("调出");
                AdjustCheckDto checked = new AdjustCheckDto();
                checked.setItems(new ArrayList<>(Arrays.asList(manual, linked, bond)));
                doReturn(checked).when(service).checkAdjust(any(AdjustCheckReq.class));
                ForbiddenPoolAdjustCheckReq req = new ForbiddenPoolAdjustCheckReq();
                req.setCompanyCode("C10001");
                req.setCompanyShortName("Excel旧名称");
                ForbiddenPoolAdjustCheckReq.CheckItem input = new ForbiddenPoolAdjustCheckReq.CheckItem();
                input.setTargetPoolId(15L);
                input.setAdjustMode(direction);
                req.setItems(Collections.singletonList(input));

                AdjustCheckDto result = service.checkCompanyAdjust(req);

                for (AdjustCheckDto.CheckResultItem item : result.getItems().subList(0, 2)) {
                    assertThat(item.getSecurityCode()).isEqualTo("C10001");
                    assertThat(item.getSecurityShortName()).isEqualTo("某公司有限公司");
                    assertThat(item.getSecurityType()).isEqualTo("company");
                    assertThat(item.getSourceSecurityCode()).isEqualTo("C10001");
                }
                assertThat(manual.isCanAdjust()).isEqualTo(canAdjust);
                assertThat(manual.getFailReasons()).hasSize(canAdjust ? 0 : 1);
                assertThat(bond.getSecurityCode()).isEqualTo("B001");
                assertThat(bond.getSecurityShortName()).isEqualTo("24交投信用");
                assertThat(bond.getSecurityType()).isEqualTo("company_bond");
            }
        }
    }

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

    /** 验证提交复核不会把旗下不同债券的调出池误算到主体。 */
    @Test
    public void validateRequiredMutexOutboundShouldSeparateDifferentSecurities() throws Exception {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityPoolAdjustSubmitReq req = buildCompanyMutexSubmitReq("B002");
        Object shared = buildSubmitSharedData();
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");

        ReflectionTestUtils.invokeMethod(service, "validateRequiredMutexOutboundOnSubmit", req, shared);
    }

    /** 验证同一主体同时调出互斥池时仍会被提交复核拦截。 */
    @Test(expected = BizException.class)
    public void validateRequiredMutexOutboundShouldRejectSameSecurityConflict() throws Exception {
        ForbiddenPoolAdjustService service = buildService(mock(ForbiddenPoolAdjustMapper.class));
        SecurityPoolAdjustSubmitReq req = buildCompanyMutexSubmitReq("C10001");
        Object shared = buildSubmitSharedData();

        ReflectionTestUtils.invokeMethod(service, "validateRequiredMutexOutboundOnSubmit", req, shared);
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
        when(poolMapper.queryPoolByIdsList(anyList()))
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

    /** 观察池和新增主体池也必须为旗下债券预占容量。 */
    @Test
    public void finalRecheckShouldCountOnlySubmittedItems() {
        for (Long poolId : Arrays.asList(16L, 23L, 999L)) {
            ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
            // 构建主体及尚未入池债券，目标池仅剩一个名额
            ForbiddenPoolAdjustService service = buildService(mapper);
            InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
            InvestmentPoolBo pool = buildPool(poolId, "目标池", "observe");
            pool.setMaxCapacity(2L);
            when(poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
            // 构建主体日志和当前主数据
            IpAdjustLogBo log = buildCompanyLog();
            log.setTargetPoolId(poolId);
            SecurityInfoBo company = new SecurityInfoBo();
            company.setWindCode(log.getSecurityCode());
            company.setSecurityType("company");
            when(mapper.queryCompanySecurityBoByCode(log.getSecurityCode())).thenReturn(company);
            when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
            when(mapper.queryPoolCurrentCount(poolId)).thenReturn(1);
            service.recheckBeforeFinalApproval(Collections.singletonList(log));
            verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
        }
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
        ForbiddenPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new ForbiddenPoolAdjustService());
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, "禁投池", "forbidden");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", null, null);
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

        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", "C10001", null);

        verify(attachmentService).validateCreditReportSources(Collections.singletonList(100L), true);
    }

    /** 生效时仅处理已提交项目，历史债券互斥调出仍可完成 */
    @Test
    public void applyPoolStatusShouldOnlyApplyExplicitItems() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        IpAdjustLogBo company = buildCompanyLog();
        IpAdjustLogBo legacyBond = new IpAdjustLogBo();
        legacyBond.setSecurityCode("B001");
        legacyBond.setSecurityType("mtn");
        legacyBond.setTargetPoolId(16L);
        legacyBond.setAdjustMode("调出");
        when(mapper.addPoolStatus(company)).thenReturn(1);
        when(mapper.deletePoolStatusSoft("B001", 16L)).thenReturn(1);

        service.applyPoolStatusChanges(Arrays.asList(company, legacyBond));

        verify(mapper).addPoolStatus(company);
        verify(mapper).deletePoolStatusSoft("B001", 16L);
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 构建服务并注入公共依赖。 */
    private ForbiddenPoolAdjustService buildService(ForbiddenPoolAdjustMapper mapper) {
        ForbiddenPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new ForbiddenPoolAdjustService());
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        return service;
    }

    /** 构建主体数据。 */
    private ForbiddenPoolAdjustDto buildCompany(String companyCode) {
        ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
        company.setCompanyCode(companyCode);
        company.setCompanyShortName("某公司");
        company.setCompanyFullName("某公司有限公司");
        return company;
    }

    /**
     * 验证债券特殊策略类流程（发起人→多层 auto→结束）判为直通。
     * 旧逻辑只认「发起人后直接 end」，会误判为非直通导致联动漏落池。
     */
    @Test
    public void isDirectFlowShouldAcceptInitiatorThenAutoChain() throws Exception {
        ForbiddenPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new ForbiddenPoolAdjustService());
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
        ForbiddenPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new ForbiddenPoolAdjustService());
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

    /** 构造主体调入及主体、旗下债券互斥调出请求。 */
    private SecurityPoolAdjustSubmitReq buildCompanyMutexSubmitReq(String thirdLevelSecurityCode) {
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("C10001");
        req.setSecurityType("company");
        SecurityPoolAdjustSubmitReq.AdjustItem inbound = new SecurityPoolAdjustSubmitReq.AdjustItem();
        inbound.setSecurityCode("C10001");
        inbound.setTargetPoolId(15L);
        inbound.setAdjustMode("调入");
        inbound.setItemTag("manual");
        SecurityPoolAdjustSubmitReq.AdjustItem companyOutbound = new SecurityPoolAdjustSubmitReq.AdjustItem();
        companyOutbound.setSecurityCode("C10001");
        companyOutbound.setTargetPoolId(2L);
        companyOutbound.setAdjustMode("调出");
        companyOutbound.setItemTag("mutex");
        SecurityPoolAdjustSubmitReq.AdjustItem bondOutbound = new SecurityPoolAdjustSubmitReq.AdjustItem();
        bondOutbound.setSecurityCode(thirdLevelSecurityCode);
        bondOutbound.setTargetPoolId(4L);
        bondOutbound.setAdjustMode("调出");
        bondOutbound.setItemTag("mutex");
        req.setItems(Arrays.asList(inbound, companyOutbound, bondOutbound));
        return req;
    }

    /** 构造提交阶段共享数据：禁投池调入要求主体从一级库调出，一级库与三级库互斥。 */
    private Object buildSubmitSharedData() throws Exception {
        SecurityInfoBo company = new SecurityInfoBo();
        company.setWindCode("C10001");
        company.setSecurityType("company");
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(15L, buildPool(15L, "债券禁止库", "forbidden"));
        poolMap.put(1L, buildPool(1L, "信用债大库", "credit_bond"));
        poolMap.put(2L, buildChildPool(2L, 1L, "一级库", "credit_bond"));
        poolMap.put(4L, buildChildPool(4L, 1L, "三级库", "credit_bond"));
        Map<Long, Map<String, List<Long>>> relationMap = new HashMap<>();
        Map<String, List<Long>> forbiddenRelations = new HashMap<>();
        forbiddenRelations.put("in_mutex", Collections.singletonList(2L));
        relationMap.put(15L, forbiddenRelations);
        Map<String, List<Long>> firstLevelRelations = new HashMap<>();
        firstLevelRelations.put("in_mutex", Collections.singletonList(4L));
        relationMap.put(2L, firstLevelRelations);
        Set<Long> currentPoolIds = new HashSet<>(Collections.singletonList(2L));
        Class<?> sharedClass = Class.forName(
                "com.znty.rrs.service.ForbiddenPoolAdjustService$SubmitSharedData");
        Constructor<?> ctor = sharedClass.getDeclaredConstructor(
                SecurityInfoBo.class, Map.class, Set.class, Map.class,
                boolean.class, boolean.class, boolean.class, Map.class,
                ForbiddenPoolAdjustService.BatchNoContext.class);
        ctor.setAccessible(true);
        return ctor.newInstance(company, poolMap, currentPoolIds, relationMap,
                false, false, false, Collections.emptyMap(),
                new ForbiddenPoolAdjustService.BatchNoContext());
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

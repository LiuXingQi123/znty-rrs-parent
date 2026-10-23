package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
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
import org.junit.Assume;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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

    /** 验证主体调入债券禁止库时，债券同步入池并从当前实际所在的关系池自动调出。 */
    @Test
    public void syncCompanyBondsOnDirectShouldInsertBondAndAutoOutRelatedPool() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        SecurityInfoBo newBond = buildBond("B002");
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList(eq("C10001"), eq(15L),
                any(CompanyBondTypeScopeBo.class)))
                .thenReturn(Collections.singletonList(newBond));
        when(mapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(15L, "in_mutex", 3L),
                buildRelation(3L, "in_restrict", 15L)));
        when(poolMapper.queryPoolByIdsList(anyList()))
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
        assertThat(outboundLog.getAdjustReason()).contains("调入“债券禁止库”", "自动调出“二级库”");
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
        when(mapper.queryCompanyInboundBondForAutoList(eq("C10001"), eq(15L),
                any(CompanyBondTypeScopeBo.class)))
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

    /** 主体出池时，同批已显式列出的债券调出不再重复落地。 */
    @Test
    public void outboundSyncShouldLeaveExplicitBondToBatchApply() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        // 观察池主体和债券同批出池，债券已有真实调出日志
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyOutboundBondForAutoList(eq("C10001"), eq(16L),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Collections.singletonList(buildBond("B002")));
        IpAdjustLogBo log = buildCompanyLog();
        log.setTargetPoolId(16L);
        log.setAdjustMode("调出");

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBonds", log, Collections.singleton("B002|16"));

        verify(mapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
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
        for (Long targetPoolId : Arrays.asList(15L, 16L, 23L, 999L)) {
            ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
            InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
            ForbiddenPoolAdjustService service = buildService(mapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
            when(mapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                    buildRelation(targetPoolId, "in_mutex", 3L),
                    buildRelation(3L, "in_restrict", targetPoolId)));
            ForbiddenPoolAdjustDto.CompanyBond bond = new ForbiddenPoolAdjustDto.CompanyBond();
            bond.setWindCode("B002");
            bond.setShortName("测试债券");
            bond.setSecurityType("company_bond");
            bond.setTargetPoolId(3L);
            when(mapper.queryCompanyBondMutexOutList(eq("C10001"), eq(targetPoolId),
                    eq(Collections.singletonList(3L)), any(CompanyBondTypeScopeBo.class)))
                    .thenReturn(Collections.singletonList(bond));
            when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(
                    buildPool(1L, "信用债大库", "credit_bond"),
                    buildChildPool(3L, 1L, "二级库", "credit_bond")));
            AdjustCheckDto.CheckResultItem manual = new AdjustCheckDto.CheckResultItem();
            manual.setTargetPoolId(targetPoolId);
            manual.setAdjustMode("调入");
            manual.setItemTag("manual");
            manual.setAdjustGroupKey("15_调入");
            manual.setCanAdjust(true);
            AdjustCheckDto result = new AdjustCheckDto();
            result.setItems(new ArrayList<>(Collections.singletonList(manual)));

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
    }

    /** 多个主体目标池需要同一债券出池时，预览只保留一个真实调出项。 */
    @Test
    public void mutexPreviewShouldDeduplicateAcrossInboundPools() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        // 构建两个主体调入项，均要求旗下同一债券从池 3 调出
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                buildRelation(16L, "in_mutex", 3L), buildRelation(23L, "in_mutex", 3L)));
        ForbiddenPoolAdjustDto.CompanyBond bond = new ForbiddenPoolAdjustDto.CompanyBond();
        bond.setWindCode("B002");
        bond.setTargetPoolId(3L);
        when(mapper.queryCompanyBondMutexOutList(eq("C10001"), any(Long.class), anyList(),
                any(CompanyBondTypeScopeBo.class))).thenReturn(Collections.singletonList(bond));
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
        for (Long poolId : Arrays.asList(16L, 23L)) {
            AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
            item.setTargetPoolId(poolId);
            item.setItemTag("manual");
            item.setAdjustMode("调入");
            item.setCanAdjust(true);
            items.add(item);
        }
        AdjustCheckDto result = new AdjustCheckDto();
        result.setItems(items);

        ReflectionTestUtils.invokeMethod(service, "appendCompanyBondMutexOutItems", "C10001", result);

        assertThat(result.getItems()).hasSize(3);
        verify(mapper, times(2)).queryCompanyBondMutexOutList(eq("C10001"), any(Long.class), anyList(),
                any(CompanyBondTypeScopeBo.class));
    }

    /** 使用 H2 真实事务验证旗下债写入或软删失败后，主体及先前债券状态全部回滚。 */
    @Test
    public void companyAndBondsShouldRollbackTogetherInBothDirections() {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        for (String mode : Arrays.asList("调入", "调出")) {
            DriverManagerDataSource source = new DriverManagerDataSource(
                    "jdbc:h2:mem:company_sync_" + ("调入".equals(mode) ? "in" : "out") + ";DB_CLOSE_DELAY=-1", "sa", "");
            JdbcTemplate jdbc = new JdbcTemplate(source);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            jdbc.execute("CREATE TABLE pool_state (security_code VARCHAR(32))");
            if ("调出".equals(mode)) {
                jdbc.update("INSERT INTO pool_state VALUES ('C10001'), ('B001'), ('B002')");
            }
            ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
            // 将 Mapper 的状态写入接到内存库，第二只债券模拟写入失败
            ForbiddenPoolAdjustService service = buildService(mapper);
            when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
            List<SecurityInfoBo> bonds = Arrays.asList(buildBond("B001"), buildBond("B002"));
            when(mapper.queryCompanyInboundBondForAutoList(eq("C10001"), eq(16L),
                    any(CompanyBondTypeScopeBo.class))).thenReturn(bonds);
            when(mapper.queryCompanyOutboundBondForAutoList(eq("C10001"), eq(16L),
                    any(CompanyBondTypeScopeBo.class))).thenReturn(bonds);
            when(mapper.addAdjustLog(any(IpAdjustLogBo.class))).thenReturn(1);
            when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
                IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
                return "B002".equals(log.getSecurityCode()) ? 0
                        : jdbc.update("INSERT INTO pool_state VALUES (?)", log.getSecurityCode());
            });
            when(mapper.deletePoolStatusSoft(any(String.class), eq(16L))).thenAnswer(invocation -> {
                String code = (String) invocation.getArguments()[0];
                return "B002".equals(code) ? 0 : jdbc.update("DELETE FROM pool_state WHERE security_code = ?", code);
            });
            // 通过与提交、审批相同的事务边界应用主体和债券变化
            IpAdjustLogBo log = buildCompanyLog();
            log.setTargetPoolId(16L);
            log.setAdjustMode(mode);
            assertThatThrownBy(() -> transaction.execute(status -> {
                service.applyPoolStatusChanges(Collections.singletonList(log));
                return null;
            })).isInstanceOf(BizException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pool_state", Integer.class))
                    .isEqualTo("调入".equals(mode) ? 0 : 3);
            jdbc.execute("DROP ALL OBJECTS");
        }
    }

    /** 验证所有主体目标池的入池和出池均生成同批债券日志并改变对应状态。 */
    @Test
    public void syncCompanyBondsShouldFollowEveryTargetPoolAndDirection() {
        for (Long poolId : Arrays.asList(15L, 16L, 17L, 23L, 999L)) {
            for (String mode : Arrays.asList("调入", "调出")) {
                ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
                // 构建独立服务，避免不同目标池的调用验证相互干扰
                ForbiddenPoolAdjustService service = buildService(mapper);
                when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
                List<SecurityInfoBo> bonds = new ArrayList<>();
                for (String type : Arrays.asList("company_bond", "abs", "crmw")) {
                    // 各类 bond 子类型均跟随主体
                    SecurityInfoBo bond = buildBond("B_" + type);
                    bond.setSecurityType(type);
                    bonds.add(bond);
                }
                when(mapper.queryCompanyInboundBondForAutoList(eq("C10001"), eq(poolId),
                        any(CompanyBondTypeScopeBo.class))).thenReturn(bonds);
                when(mapper.queryCompanyOutboundBondForAutoList(eq("C10001"), eq(poolId),
                        any(CompanyBondTypeScopeBo.class))).thenReturn(bonds);
                when(mapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(invocation -> {
                    ((IpAdjustLogBo) invocation.getArguments()[0]).setId(99L);
                    return 1;
                });
                when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
                when(mapper.deletePoolStatusSoft(any(String.class), eq(poolId))).thenReturn(1);
                // 主体日志中的目标池、方向、批次必须完整传递给债券
                IpAdjustLogBo companyLog = buildCompanyLog();
                companyLog.setTargetPoolId(poolId);
                companyLog.setTargetPoolName("目标池" + poolId);
                companyLog.setAdjustMode(mode);

                ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);

                ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
                verify(mapper, times(3)).addAdjustLog(captor.capture());
                for (IpAdjustLogBo log : captor.getAllValues()) {
                    assertThat(log.getTargetPoolId()).isEqualTo(poolId);
                    assertThat(log.getAdjustMode()).isEqualTo(mode);
                    assertThat(log.getAdjustBatchNo()).isEqualTo(companyLog.getAdjustBatchNo());
                    assertThat(log.getAuditStatus()).isEqualTo("20");
                }
                if ("调入".equals(mode)) {
                    verify(mapper, times(3)).addPoolStatus(any(IpAdjustLogBo.class));
                    verify(mapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
                } else {
                    verify(mapper, times(3)).deletePoolStatusSoft(any(String.class), eq(poolId));
                    verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
                }
            }
        }
    }

    /** 普通债券调库不触发主体跟债，避免递归同步。 */
    @Test
    public void syncCompanyBondsShouldSkipNonCompany() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        IpAdjustLogBo companyLog = new IpAdjustLogBo();
        companyLog.setSecurityCode("C10001");
        companyLog.setSecurityType("company_bond");
        companyLog.setAdjustMode("调入");
        companyLog.setTargetPoolId(16L);
        companyLog.setTargetPoolName("观察池");
        companyLog.setPoolType("observe");

        ReflectionTestUtils.invokeMethod(service, "syncCompanyBondsOnDirect", companyLog);

        verify(mapper).queryCategoryTypeBySecurityType("company_bond");
        verify(mapper, never()).queryCompanyInboundBondForAutoList(
                any(String.class), any(Long.class), any(CompanyBondTypeScopeBo.class));
        verify(mapper, never()).queryCompanyOutboundBondForAutoList(
                any(String.class), any(Long.class), any(CompanyBondTypeScopeBo.class));
        verify(mapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 观察池和新增主体池也必须为旗下债券预占容量。 */
    @Test
    public void finalRecheckShouldCountBondsForEveryCompanyPool() {
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
            when(mapper.queryCompanyInboundBondForAutoList(eq(log.getSecurityCode()), eq(poolId),
                    any(CompanyBondTypeScopeBo.class))).thenReturn(Collections.singletonList(buildBond("B001")));

            assertThatThrownBy(() -> service.recheckBeforeFinalApproval(Collections.singletonList(log)))
                    .isInstanceOf(BizException.class).hasMessageContaining("无法容纳主体及同步债券");
            verify(mapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
        }
    }

    /** 验证同步债券调入写入数量异常时阻断整批事务。 */
    @Test(expected = BizException.class)
    public void syncCompanyBondsShouldFailWhenInboundInsertCountIsInvalid() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList(eq("C10001"), eq(15L),
                any(CompanyBondTypeScopeBo.class)))
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
        company.setCompanyFullName("某公司有限公司");
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

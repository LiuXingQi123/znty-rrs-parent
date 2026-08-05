package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
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

    /** 验证主体提交时固定写入 company 类型，不依赖前端传入类型。 */
    @Test
    public void convertCompanySubmitReqShouldUseFixedCompanySecurityType() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ForbiddenPoolAdjustSubmitReq req = new ForbiddenPoolAdjustSubmitReq();
        req.setItems(Collections.<ForbiddenPoolAdjustSubmitReq.AdjustItem>emptyList());
        ForbiddenPoolAdjustDto company = buildCompany("C10001");

        SecurityPoolAdjustSubmitReq result = ReflectionTestUtils.invokeMethod(
                service, "convertCompanySubmitReq", req, company);

        assertThat(result.getSecurityType()).isEqualTo("company");
        assertThat(result.getSecurityCode()).isEqualTo("C10001");
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

    /** 验证可调投资池仅返回禁投池、观察池、黑名单质押库和重点观察名单。 */
    @Test
    public void queryCompanyAdjustPoolListShouldOnlyReturnConfiguredRiskPools() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(poolMapper.queryPoolByIdsList(anyListOf(Long.class)))
                .thenReturn(Arrays.asList(
                        buildPool(15L, "禁投池", "forbidden"),
                        buildPool(16L, "观察池", "observe"),
                        buildPool(17L, "黑名单质押库", "blacklist"),
                        buildPool(23L, "重点观察名单", "restricted")));
        when(poolMapper.queryMutexRelationList()).thenReturn(Collections.emptyList());
        when(mapper.queryPoolCurrentCountList()).thenReturn(Collections.<PoolDto>emptyList());
        ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
        req.setCurrentUserId("1");

        List<PoolDto> result = service.queryCompanyAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsOnly(15L, 16L, 17L, 23L);
    }

    /** 验证主体调入债券禁止库时同步 SQL 已筛选出的未到期旗下债券。 */
    @Test
    public void syncCompanyBondsOnDirectShouldInsertOnlyActualBondChange() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustService service = buildService(mapper);
        SecurityInfoBo newBond = buildBond("B002");
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyInboundBondForAutoList("C10001", 15L))
                .thenReturn(Collections.singletonList(newBond));
        when(mapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
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
        verify(mapper).addAdjustLog(captor.capture());
        IpAdjustLogBo autoLog = captor.getValue();
        assertThat(autoLog.getSecurityCode()).isEqualTo("B002");
        assertThat(autoLog.getAdjustType()).isEqualTo("自动调整");
        assertThat(autoLog.getAdjustBatchNo()).isEqualTo(companyLog.getAdjustBatchNo());
        verify(mapper).addPoolStatus(autoLog);
        verify(mapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
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

package com.znty.rrs.service;

import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustSharedData;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRMW 池调库服务单元测试。
 *
 * <p>覆盖报告必填校验（{@code checkReportRequired}）与 CRMW组合校验（调入凭证已在池、调出组合在池），
 * 确认 CRMW 链路与证券池链路同构，并落实 CRMW 链路特有的凭证级校验。
 */
public class CrmwPoolAdjustServiceTest {

    /** 当前池查询必须使用完整 CRMW 凭证与标的证券组合键。 */
    @Test
    public void queryCrmwPoolStatusShouldUseCompleteCombinationKey() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolService", mock(InvestmentPoolService.class));
        CrmwPoolAdjustReq req = new CrmwPoolAdjustReq();
        req.setSecurityCode("BOND001");
        req.setCrmwScode("CRMW001");
        req.setCrmwStype("crmw");
        when(mapper.querySecurityPoolStatusList("BOND001", "CRMW001", "crmw"))
                .thenReturn(Collections.emptyList());
        when(mapper.queryIssuerPoolStatusList("BOND001")).thenReturn(Collections.emptyList());

        service.queryCrmwPoolStatus(req);

        verify(mapper).querySecurityPoolStatusList("BOND001", "CRMW001", "crmw");
    }

    /** CRMW 链路报告必填校验：限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("CRMW报告池");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", null);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("any 限制且无报告时应抛出异常");
    }

    /** CRMW 链路报告必填校验：限制为 internal 且选择了内部报告库附件时应通过。 */
    @Test
    public void checkReportRequiredShouldPassWhenInternalAndHasSourceAttachment() {
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(1L));
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("CRMW报告池");
        // internal 限制，已从内部报告库选择附件，应通过
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", null);
    }

    /** CRMW组合校验（调入）：凭证已在目标池时应抛出异常。 */
    @Test
    public void checkCrmwInboundCombinationShouldFailWhenCrmwAlreadyInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 凭证已在池
        when(mapper.queryCrmwAlreadyInPool(anyString(), anyString(), anyLong())).thenReturn(true);
        try {
            ReflectionTestUtils.invokeMethod(service, "checkCrmwInboundCombination", buildCrmwReq(), buildItem());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("已经在池");
            return;
        }
        throw new AssertionError("凭证已在池时应抛出异常");
    }

    /** CRMW组合校验（调入）：凭证不在池时应通过，pending 由组合池组查询统一处理。 */
    @Test
    public void checkCrmwInboundCombinationShouldPassWhenCrmwFree() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 凭证不在池，应通过
        when(mapper.queryCrmwAlreadyInPool(anyString(), anyString(), anyLong())).thenReturn(false);
        ReflectionTestUtils.invokeMethod(service, "checkCrmwInboundCombination", buildCrmwReq(), buildItem());
    }

    /** CRMW组合校验（调出）：组合不在目标池时应抛出异常。 */
    @Test
    public void checkCrmwOutboundCombinationShouldFailWhenComboNotInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 组合不在池
        when(mapper.queryCrmwComboInPool(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);
        try {
            ReflectionTestUtils.invokeMethod(service, "checkCrmwOutboundCombination", buildCrmwReq(), buildItem());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("组合不在池");
            return;
        }
        throw new AssertionError("组合不在池时应抛出异常");
    }

    /** CRMW 证券信息合并不应清空页面不可编辑字段。 */
    @Test
    public void mergeSecurityInfoShouldKeepNonEditableFields() {
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        SecurityInfoBo current = new SecurityInfoBo();
        current.setShortName("原简称");
        current.setDateRepurchaseExists(new BigDecimal("365.0000"));
        current.setGuarantFlag(1);
        current.setGuarantType("连带责任担保");
        current.setAbsFlag(1);
        SecurityInfoBo changed = new SecurityInfoBo();
        changed.setShortName("新简称");

        ReflectionTestUtils.invokeMethod(service, "mergeSecurityInfo", current, changed);

        assertThat(current.getShortName()).isEqualTo("新简称");
        assertThat(current.getDateRepurchaseExists()).isEqualByComparingTo("365");
        assertThat(current.getGuarantFlag()).isEqualTo(1);
        assertThat(current.getGuarantType()).isEqualTo("连带责任担保");
        assertThat(current.getAbsFlag()).isEqualTo(1);
    }

    /** CRMW 调入状态写入数量异常时应阻断最终落池。 */
    @Test
    public void applyPoolStatusChangesShouldFailWhenInboundInsertCountUnexpected() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(1L);
        log.setCrmwScode("CRMW001");
        log.setAdjustMode("调入");
        log.setTargetPoolName("CRMW池");
        when(mapper.addPoolStatus(log)).thenReturn(0);

        assertThatThrownBy(() -> service.applyPoolStatusChanges(Collections.singletonList(log)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入池状态写入异常");
    }

    /** CRMW组合校验（调出）：组合在目标池时应通过。 */
    @Test
    public void checkCrmwOutboundCombinationShouldPassWhenComboInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 组合在池，应通过
        when(mapper.queryCrmwComboInPool(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "checkCrmwOutboundCombination", buildCrmwReq(), buildItem());
    }

    /** 验证 CRMW 调库链路命中特殊审批流程。 */
    @Test
    public void crmwAdjustShouldResolveSpecialInboundFlow() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound"))
                .thenReturn(buildFlowDefinition(108L, "bond:special-inbound", "债券特殊策略入库流程"));

        AdjustSharedData shared = buildSpecialInboundShared(19L, 20L, "CRMW核心库", "CRMW关注库");
        AdjustCheckDto.FlowOption option = ReflectionTestUtils.invokeMethod(
                service, "resolveSpecialInboundFlowOption",
                buildPool(19L, "CRMW核心库"), shared);

        assertThat(option.getFlowType()).isEqualTo("specialInbound");
        assertThat(option.getFlowKey()).isEqualTo("bond:special-inbound");
    }

    /** 构建 CRMW 调库提交请求（含凭证与标的证券）。 */
    private CrmwPoolAdjustSubmitReq buildCrmwReq() {
        CrmwPoolAdjustSubmitReq req = new CrmwPoolAdjustSubmitReq();
        req.setSecurityCode("220205");
        req.setCrmwScode("012345");
        req.setCrmwStype("crmw");
        return req;
    }

    /** 构建调库项。 */
    private CrmwPoolAdjustSubmitReq.AdjustItem buildItem() {
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(10L);
        return item;
    }

    /** 构造特殊审批命中共享数据。 */
    private AdjustSharedData buildSpecialInboundShared(Long targetPoolId, Long currentPoolId,
                                                       String targetPoolName, String currentPoolName) {
        InvestmentPoolBo targetPool = buildPool(targetPoolId, targetPoolName);
        InvestmentPoolBo currentPool = buildPool(currentPoolId, currentPoolName);
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(targetPoolId, targetPool);
        poolMap.put(currentPoolId, currentPool);

        Map<String, List<Long>> targetRelations = new HashMap<>();
        targetRelations.put(RelationType.IN_MUTEX.getCode(), Collections.singletonList(currentPoolId));
        Map<Long, Map<String, List<Long>>> relationMap = new HashMap<>();
        relationMap.put(targetPoolId, targetRelations);

        AdjustSharedData shared = new AdjustSharedData();
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(Collections.singleton(currentPoolId));
        shared.setPoolRelationMap(relationMap);
        return shared;
    }

    private InvestmentPoolBo buildPool(Long id, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName(poolName);
        return pool;
    }

    private FlowDefinitionBo buildFlowDefinition(Long id, String flowKey, String name) {
        FlowDefinitionBo flow = new FlowDefinitionBo();
        flow.setId(id);
        flow.setFlowKey(flowKey);
        flow.setName(name);
        return flow;
    }
}

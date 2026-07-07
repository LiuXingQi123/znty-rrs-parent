package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CRMW 池调库服务单元测试。
 *
 * <p>覆盖报告必填校验（{@code checkReportRequired}）与 CRMW组合校验（调入凭证已在池/凭证审批中、调出组合在池），
 * 确认 CRMW 链路与证券池链路同构，并落实 CRMW 链路特有的凭证级校验。
 */
public class CrmwPoolAdjustServiceTest {

    /** CRMW 链路报告必填校验：限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("CRMW报告池");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any");
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
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportSourceAttachmentIds(java.util.Collections.singletonList(1L));
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("CRMW报告池");
        // internal 限制，已从内部报告库选择附件，应通过
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal");
    }

    /** CRMW组合校验（调入）：凭证已在目标池时应抛出异常。 */
    @Test
    public void checkCrmwInboundCombinationShouldFailWhenCrmwAlreadyInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 凭证已在池
        when(mapper.queryCrmwAlreadyInPool(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        try {
            ReflectionTestUtils.invokeMethod(service, "checkCrmwInboundCombination", buildCrmwReq(), buildItem());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("已经在池");
            return;
        }
        throw new AssertionError("凭证已在池时应抛出异常");
    }

    /** CRMW组合校验（调入）：凭证存在进行中流程时应抛出异常。 */
    @Test
    public void checkCrmwInboundCombinationShouldFailWhenCrmwPendingWorkflow() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 凭证不在池，但有进行中流程
        when(mapper.queryCrmwAlreadyInPool(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);
        when(mapper.queryCrmwPendingWorkflow(anyString(), anyString(), anyString())).thenReturn(true);
        try {
            ReflectionTestUtils.invokeMethod(service, "checkCrmwInboundCombination", buildCrmwReq(), buildItem());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("正在审批中");
            return;
        }
        throw new AssertionError("凭证审批中时应抛出异常");
    }

    /** CRMW组合校验（调入）：凭证不在池且无进行中流程时应通过。 */
    @Test
    public void checkCrmwInboundCombinationShouldPassWhenCrmwFree() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 凭证不在池、无进行中流程，应通过
        when(mapper.queryCrmwAlreadyInPool(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);
        when(mapper.queryCrmwPendingWorkflow(anyString(), anyString(), anyString())).thenReturn(false);
        ReflectionTestUtils.invokeMethod(service, "checkCrmwInboundCombination", buildCrmwReq(), buildItem());
    }

    /** CRMW组合校验（调出）：组合不在目标池时应抛出异常。 */
    @Test
    public void checkCrmwOutboundCombinationShouldFailWhenComboNotInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 组合不在池
        when(mapper.queryCrmwComboInPool(anyString(), anyString(), anyString(), anyString(), anyLong())).thenReturn(false);
        try {
            ReflectionTestUtils.invokeMethod(service, "checkCrmwOutboundCombination", buildCrmwReq(), buildItem());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("组合不在池");
            return;
        }
        throw new AssertionError("组合不在池时应抛出异常");
    }

    /** CRMW组合校验（调出）：组合在目标池时应通过。 */
    @Test
    public void checkCrmwOutboundCombinationShouldPassWhenComboInPool() {
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        // 组合在池，应通过
        when(mapper.queryCrmwComboInPool(anyString(), anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "checkCrmwOutboundCombination", buildCrmwReq(), buildItem());
    }

    /** 构建 CRMW 调库提交请求（含凭证与标的证券）。 */
    private CrmwPoolAdjustSubmitReq buildCrmwReq() {
        CrmwPoolAdjustSubmitReq req = new CrmwPoolAdjustSubmitReq();
        req.setSecurityCode("220205");
        req.setCrmwScode("012345");
        req.setCrmwMktcode("IB");
        req.setCrmwStype("crmw");
        return req;
    }

    /** 构建调库项。 */
    private CrmwPoolAdjustSubmitReq.AdjustItem buildItem() {
        CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(10L);
        return item;
    }
}

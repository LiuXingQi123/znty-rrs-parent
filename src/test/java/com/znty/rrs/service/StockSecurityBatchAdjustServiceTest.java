package com.znty.rrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.constants.ProductInboundPoolCodes;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustSubmitReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 存量证券批量调整服务测试
 */
public class StockSecurityBatchAdjustServiceTest {

    /** 存量入口原样透传产品库快速资格与材料豁免信息 */
    @Test
    public void checkResultShouldPassThroughProductMaterialExemption() {
        StockSecurityBatchAdjustService service = AbsIdentityTestSupport.withIdentity(new StockSecurityBatchAdjustService());
        StockSecurityBatchAdjustSubmitReq.SecurityItem security = new StockSecurityBatchAdjustSubmitReq.SecurityItem();
        security.setSecurityCode("BOND.IB");
        AdjustCheckDto.FlowOption fast = new AdjustCheckDto.FlowOption();
        fast.setFlowType("simpleInbound");
        fast.setMaterialExemptionType("productSource");
        fast.setSelectable(true);
        fast.setRecommended(true);
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setFlowOptions(Collections.singletonList(fast));
        item.setMaterialExemptionType("productSource");
        item.setAdjustGroupKey("product-group");
        StockSecurityBatchAdjustDto.CheckResultItem result = ReflectionTestUtils.invokeMethod(
                service, "buildBatchCheckResult", security, item);
        assertThat(result.getFlowOptions()).hasSize(1);
        assertThat(result.getFlowOptions().get(0).getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(result.getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(result.getFlowOptions().get(0).isRecommended()).isTrue();
    }

    /** 存量入口提交批量流程时保留校验给出的材料资格和所选流程 */
    @Test
    public void buildSingleSubmitReqShouldKeepQualificationAndSelectedFlow() {
        StockSecurityBatchAdjustService service = AbsIdentityTestSupport.withIdentity(new StockSecurityBatchAdjustService());
        StockSecurityBatchAdjustSubmitReq request = new StockSecurityBatchAdjustSubmitReq();
        StockSecurityBatchAdjustSubmitReq.AdjustItem item = new StockSecurityBatchAdjustSubmitReq.AdjustItem();
        item.setSecurityCode("BOND.IB");
        item.setItemTag("manual");
        item.setFlowId(107L);
        item.setFlowType("batchInbound");
        item.setMaterialExemptionType("productSource");
        SecurityPoolAdjustSubmitReq result = ReflectionTestUtils.invokeMethod(service, "buildSingleSubmitReq",
                request, Collections.singletonList(item));
        assertThat(result.getItems().get(0).getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(result.getItems().get(0).getFlowType()).isEqualTo("batchInbound");
        assertThat(result.getItems().get(0).getFlowId()).isEqualTo(107L);
    }

    /** 调入未选来源池时应拒绝 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRequireSourcePoolForInbound() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        StockSecurityBatchAdjustService service = AbsIdentityTestSupport.withIdentity(new StockSecurityBatchAdjustService());
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("in");
        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 调出不校验来源池参数 */
    @Test
    public void validateSecurityPageReqShouldIgnoreSourcePoolForOutbound() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        StockSecurityBatchAdjustService service = AbsIdentityTestSupport.withIdentity(new StockSecurityBatchAdjustService());
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("out");
        req.setSourcePoolIds(Collections.singletonList(99999L));

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 从白名单解析当前环境的 CRMW 池 ID，仅在本次勾选时传给查询 */
    @Test
    public void validateSecurityPageReqShouldResolveSelectedCrmwSourcePoolId() throws Exception {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);
        long nextPoolId = 801L;
        for (String code : ProductInboundPoolCodes.SOURCES) {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(ProductInboundPoolCodes.CRMW.equals(code) ? 800L : nextPoolId++);
            pool.setPoolCode(code);
            pool.setIsDeleted(0);
            when(poolMapper.queryPoolByCode(pool.getPoolCode())).thenReturn(pool);
        }

        ObjectMapper json = new ObjectMapper();
        StockSecurityBatchAdjustReq req = json.readValue(
                "{\"poolId\":33,\"direction\":\"in\",\"sourcePoolIds\":[800],\"crmwSourcePoolId\":999}",
                StockSecurityBatchAdjustReq.class);
        assertThat(req.getCrmwSourcePoolId()).isNull();
        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
        assertThat(req.getCrmwSourcePoolId()).isEqualTo(800L);
        assertThat(json.valueToTree(req).has("crmwSourcePoolId")).isFalse();

        req.setSourcePoolIds(Collections.singletonList(801L));
        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
        assertThat(req.getCrmwSourcePoolId()).isNull();
    }

}

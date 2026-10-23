package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.AdjustSharedData;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRMW 同一凭证下的标的跨市场整组校验。 */
public class CrmwPoolAdjustCrossMarketTest {
    /** 待测服务。 */
    private CrmwPoolAdjustService service;
    /** 组合状态查询桩。 */
    private CrmwPoolAdjustMapper mapper;
    /** 同券关联查询桩。 */
    private SecurityMarketService markets;
    /** 主组合上下文。 */
    private AdjustSharedData shared;
    /** 目标 CRMW 池。 */
    private InvestmentPoolBo pool;

    /** 建立一个凭证与三个市场标的组合。 */
    @Before
    public void setUp() {
        service = new CrmwPoolAdjustService();
        mapper = mock(CrmwPoolAdjustMapper.class);
        markets = mock(SecurityMarketService.class);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityMarketService", markets);
        ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        when(markets.queryRelatedMarketCodes("A.IB")).thenReturn(Arrays.asList(market("A.SH"), market("A.SZ")));
        when(mapper.querySecurityBoByCode("A.SH")).thenReturn(security("A.SH"));
        when(mapper.querySecurityBoByCode("A.SZ")).thenReturn(security("A.SZ"));
        when(mapper.queryCategoryTypeBySecurityType("mtn")).thenReturn("bond");
        pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("CRMW库");
        pool.setPoolType("crmw");
        pool.setVarietyCodes("[\"bond\"]");
        shared = new AdjustSharedData();
        shared.setSecurityInfo(security("A.IB"));
        shared.setCrmwScode("C.IB");
        shared.setCrmwStype("crmw");
        shared.setPoolMap(Collections.singletonMap(10L, pool));
        shared.setPoolRelationMap(Collections.emptyMap());
        shared.setCurrentPoolIds(Collections.emptySet());
        shared.setPendingPoolGroupIds(Collections.emptySet());
        shared.setRequestInPoolIds(Collections.singleton(10L));
        shared.setRequestOutPoolIds(Collections.emptySet());
    }

    /** 关联标的在途时阻断整个凭证下的同券组。 */
    @Test
    public void pendingRelatedCombinationShouldBlockAllMarkets() {
        when(mapper.queryPendingManualTargetPoolIdList("A.SH", "C.IB", "crmw", null))
                .thenReturn(Collections.singletonList(10L));
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).hasSize(3).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(0).getFailReasons()).anyMatch(reason -> reason.contains("A.SH"));
    }

    /** 容量不足以容纳三个市场时不能只入部分组合。 */
    @Test
    public void insufficientCapacityShouldBlockWholeGroup() {
        pool.setMaxCapacity(2L);
        assertThat(expand()).allMatch(item -> !item.isCanAdjust());
    }

    /** 全部通过时仍沿用原凭证和主组合键。 */
    @Test
    public void completeGroupShouldKeepOriginalCertificate() {
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(items.subList(1, 3)).allMatch(item -> "C.IB".equals(item.getCrmwScode())
                && "C.IB|A.IB".equals(item.getSourceSecurityCode()));
    }

    /** 提交直接复用传入的关联集合，遗漏市场时报错且不查询同券关系。 */
    @Test
    public void submitShouldRejectMissingPassedMarketWithoutQuery() {
        CrmwPoolAdjustSubmitReq req = submitRequest("A.IB", "A.SZ");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateRelatedInboundItemsOnSubmit", req))
                .isInstanceOf(BizException.class).hasMessageContaining("A.SH");
        verify(markets, never()).queryRelatedMarketCodes(anyString());
    }

    /** 完整请求通过后仍按组合逐券检查在途流程，其他凭证不参与本组判断。 */
    @Test
    public void submitShouldRecheckPendingForEachActualCombination() {
        CrmwPoolAdjustSubmitReq req = submitRequest("A.IB", "A.SH", "A.SZ");
        when(mapper.queryPendingManualTargetPoolIdList("A.SH", "C.IB", "crmw", null))
                .thenReturn(Collections.singletonList(10L));
        ReflectionTestUtils.invokeMethod(service, "validateRelatedInboundItemsOnSubmit", req);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "checkSubmitPendingPoolGroups", req, shared.getPoolMap()))
                .isInstanceOf(BizException.class).hasMessageContaining("C.IB/A.SH");
        verify(markets, never()).queryRelatedMarketCodes(anyString());
    }

    /** 调用真实扩批校验，验证组合状态和容量规则。 */
    private List<AdjustCheckDto.CheckResultItem> expand() {
        AdjustCheckDto.CheckResultItem main = new AdjustCheckDto.CheckResultItem();
        main.setTargetPoolId(10L);
        main.setPoolName("CRMW库");
        main.setPoolType("crmw");
        main.setSecurityCode("A.IB");
        main.setAdjustMode("调入");
        main.setItemTag("manual");
        main.setAdjustGroupKey("manual_10");
        main.setCanAdjust(true);
        main.setFailReasons(Collections.emptyList());
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
        items.add(main);
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("A.IB");
        req.setCrmwScode("C.IB");
        req.setCrmwStype("crmw");
        ReflectionTestUtils.invokeMethod(service, "expandRelatedMarketCodeItems", req, shared, items);
        return items;
    }

    /** 构造同凭证的指定市场提交集合。 */
    private CrmwPoolAdjustSubmitReq submitRequest(String... codes) {
        CrmwPoolAdjustSubmitReq req = new CrmwPoolAdjustSubmitReq();
        req.setSecurityCode("A.IB");
        req.setCrmwScode("C.IB");
        req.setCrmwStype("crmw");
        req.setRelatedMarketCodes(Arrays.asList(market("A.SH"), market("A.SZ")));
        List<CrmwPoolAdjustSubmitReq.AdjustItem> items = new ArrayList<>();
        for (String code : codes) {
            CrmwPoolAdjustSubmitReq.AdjustItem item = new CrmwPoolAdjustSubmitReq.AdjustItem();
            item.setSecurityCode(code);
            item.setTargetPoolId(10L);
            item.setAdjustMode("调入");
            items.add(item);
        }
        req.setItems(items);
        return req;
    }

    /** 构造独立标的主档。 */
    private SecurityInfoBo security(String code) {
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode(code);
        security.setSecurityType("mtn");
        return security;
    }

    /** 构造已查询的关联市场代码。 */
    private SecurityMarketDto market(String code) {
        SecurityMarketDto result = new SecurityMarketDto();
        result.setWindCode(code);
        return result;
    }
}

package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ForbiddenAbsPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 证券池与 ABS 链路共用同券跨市场整组调库验收场景。 */
@RunWith(Parameterized.class)
public class SecurityPoolAdjustCrossMarketTest {
    /** 是否验证 ABS 链路。 */
    private final boolean abs;
    /** 待测链路服务。 */
    private Object service;
    /** 当前链路的数据访问桩。 */
    private Object mapper;
    /** 同券关联查询桩。 */
    private SecurityMarketService markets;
    /** 主券校验上下文。 */
    private AdjustSharedData shared;
    /** 本次目标池。 */
    private InvestmentPoolBo pool;

    /** 两条共享证券校验模型的链路使用同一组验收数据。 */
    @Parameterized.Parameters(name = "ABS={0}")
    public static Collection<Object[]> scenes() {
        return Arrays.asList(new Object[][]{{false}, {true}});
    }

    /** 保存待测链路。 */
    public SecurityPoolAdjustCrossMarketTest(boolean abs) {
        this.abs = abs;
    }

    /** 建立同一证券的银行间、沪市、深市代码，目标为三级库。 */
    @Before
    public void setUp() {
        service = abs ? AbsIdentityTestSupport.withIdentity(new ForbiddenAbsPoolAdjustService()) : AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        mapper = abs ? mock(ForbiddenAbsPoolAdjustMapper.class) : mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, abs ? "forbiddenAbsPoolAdjustMapper" : "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        markets = mock(SecurityMarketService.class);
        ReflectionTestUtils.setField(service, "securityMarketService", markets);
        when(markets.queryRelatedMarketCodes("A.IB")).thenReturn(Arrays.asList(market("A.SH"), market("A.SZ")));
        // 关联码保留各自的证券主档和活动流程状态
        stub("querySecurityBoByCode", security("A.SH"), "A.SH");
        stub("querySecurityBoByCode", security("A.SZ"), "A.SZ");
        stub("queryCategoryTypeBySecurityType", "bond", "mtn");
        pool = new InvestmentPoolBo();
        pool.setId(4L);
        pool.setPoolName("三级库");
        pool.setPoolType("credit_bond");
        pool.setInnerSort(3);
        pool.setVarietyCodes("[\"bond\"]");
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(pool.getId(), pool);
        shared = new AdjustSharedData();
        shared.setSecurityInfo(security("A.IB"));
        shared.setPoolMap(poolMap);
        shared.setPoolRelationMap(Collections.emptyMap());
        shared.setCurrentPoolIds(Collections.emptySet());
        shared.setPendingPoolGroupIds(Collections.emptySet());
        shared.setRequestInPoolIds(Collections.singleton(pool.getId()));
        shared.setRequestOutPoolIds(Collections.emptySet());
        shared.setReleaseRules(true);
    }

    /** 关联券已在其他批次流程中时，主券和另一关联券也不可提交。 */
    @Test
    public void pendingRelatedBondShouldBlockAllMarkets() {
        // 模拟第二批选择 A.IB，A.SH 仍被第一批占用
        stub("queryPendingManualTargetPoolIdList", Collections.singletonList(4L), "A.SH", null);
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).hasSize(3).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(0).getFailReasons()).anyMatch(reason -> reason.contains("A.SH"));
        assertThat(items.get(1).getFailReasons()).anyMatch(reason -> reason.contains("进行中"));
    }

    /** 同券沪市已过摘牌日，普通调库保留该行失败并允许另外两个市场调整 */
    @Test
    public void expiredRelatedBondShouldRemainVisibleWithoutBlockingActiveMarkets() {
        // 模拟沪市代码单独摘牌
        SecurityInfoBo expired = security("A.SH");
        expired.setDelistDate("20000101");
        stub("querySecurityBoByCode", expired, "A.SH");
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::isCanAdjust)
                .containsExactly(true, false, true);
        assertThat(items.get(1).getFailReasons()).contains("债券已到期");
    }

    /** 同券退市和在途分别保留在各自结果行 */
    @Test
    public void pendingAndDelistedMarketsShouldBothReportFailure() {
        // 沪市已退市，深市尚有在途审批
        SecurityInfoBo delisted = security("A.SH");
        delisted.setSecurityStatus("D");
        stub("querySecurityBoByCode", delisted, "A.SH");
        stub("queryPendingManualTargetPoolIdList", Collections.singletonList(4L), "A.SZ", null);
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(1).getFailReasons()).contains("债券已到期");
        assertThat(items.get(2).getFailReasons()).anyMatch(reason -> reason.contains("进行中"));
        assertThat(items.get(0).getFailReasons()).anyMatch(reason -> reason.contains("A.SZ"));
    }

    /** 调出保留未在池的代码及仍在池但已到期的代码 */
    @Test
    public void outboundShouldKeepAbsentAndExpiredRelatedMarkets() {
        // 沪市已出池，深市仍在池但已过摘牌日
        SecurityInfoBo expired = security("A.SZ");
        expired.setDelistDate("20000101");
        stub("querySecurityBoByCode", expired, "A.SZ");
        stub("querySecurityCurrentPoolIdList", Collections.singletonList(4L), "A.SZ");
        AdjustCheckDto.CheckResultItem main = manual(4L);
        main.setAdjustMode("调出");
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>(Collections.singletonList(main));
        expand(items);
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::isCanAdjust)
                .containsExactly(true, false, false);
        assertThat(items.get(1).getFailReasons()).anyMatch(reason -> reason.contains("不在目标"));
        assertThat(items.get(2).getFailReasons()).contains("债券已到期");
    }

    /** 主券到期也保留同券各市场结果 */
    @Test
    public void expiredMainShouldKeepAllMarketsInFailedGroup() {
        // 模拟主券已经命中到期调入校验
        AdjustCheckDto.CheckResultItem main = manual(4L);
        main.setCanAdjust(false);
        main.setFailReasons(Collections.singletonList("债券已到期"));
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>(Collections.singletonList(main));
        expand(items);
        assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH", "A.SZ");
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(main.getFailReasons()).containsExactly("债券已到期");
    }

    /** 最后一个关联券失败也须反向阻断先前已通过的关联券。 */
    @Test
    public void lastRelatedBondFailureShouldBlockEarlierMarkets() {
        stub("queryPendingManualTargetPoolIdList", Collections.singletonList(4L), "A.SZ", null);
        assertThat(expand()).allMatch(item -> !item.isCanAdjust());
    }

    /** 缺少主档不能通过跳过关联码产生部分入库。 */
    @Test
    public void missingRelatedBondShouldBlockWholeGroup() {
        stub("querySecurityBoByCode", null, "A.SZ");
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(0).getFailReasons()).anyMatch(reason -> reason.contains("A.SZ"));
        assertThat(items.get(2).getFailReasons()).containsExactly("关联证券代码A.SZ在系统中不存在");
    }

    /** 放开矩阵后，无内评分的同券各市场均沿用主券选择。 */
    @Test
    public void releaseRulesShouldApplyToAllMarkets() {
        assertThat(expand()).hasSize(3).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
    }

    /** 未放开矩阵时，关联券依旧执行原矩阵限制。 */
    @Test
    public void normalRulesShouldStillRejectMissingRating() {
        shared.setReleaseRules(false);
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(1).getFailReasons()).anyMatch(reason -> reason.contains("无内评分"));
    }

    /** 容量仅够两个代码时，整组三个均失败，放开矩阵也不能绕过容量。 */
    @Test
    public void capacityShouldCoverWholeMarketGroup() {
        pool.setMaxCapacity(2L);
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(2).getFailReasons()).anyMatch(reason -> reason.contains("持仓上限"));
    }

    /** 放开矩阵不放开市场限制，一个市场不支持即整组失败。 */
    @Test
    public void releaseRulesShouldNotBypassMarketRestriction() {
        pool.setMarketCodes("[\"NIB\",\"SSE\"]");
        when(markets.matchesPoolMarkets(pool.getMarketCodes(), security("A.SH"))).thenReturn(true);
        List<AdjustCheckDto.CheckResultItem> items = expand();
        assertThat(items).allMatch(item -> !item.isCanAdjust());
        assertThat(items.get(2).getFailReasons()).contains("证券不在本池投资市场范围内");
    }

    /** 只有主券失败时，其他市场不能独立通过。 */
    @Test
    public void mainBondFailureShouldBlockRelatedMarkets() {
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
        AdjustCheckDto.CheckResultItem main = manual(4L);
        main.setCanAdjust(false);
        main.setFailReasons(Collections.singletonList("主券已有在途流程"));
        items.add(main);
        // 确认原有主券到关联券的失败传递仍然有效
        expand(items);
        assertThat(items).hasSize(3).allMatch(item -> !item.isCanAdjust());
    }

    /** 同一申请的独立调库组不因另一组跨市场失败被误阻断。 */
    @Test
    public void separatePoolGroupShouldRemainAdjustable() {
        InvestmentPoolBo other = new InvestmentPoolBo();
        BeanUtils.copyProperties(pool, other);
        other.setId(8L);
        shared.getPoolMap().put(8L, other);
        stub("queryPendingManualTargetPoolIdList", Collections.singletonList(4L), "A.SH", null);
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
        items.add(manual(4L));
        items.add(manual(8L));
        // 同次展开两个独立目标池组，失败只在本组传播
        expand(items);
        assertThat(items.stream().filter(item -> Long.valueOf(4L).equals(item.getTargetPoolId())))
                .allMatch(item -> !item.isCanAdjust());
        assertThat(items.stream().filter(item -> Long.valueOf(8L).equals(item.getTargetPoolId())))
                .allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
    }

    /** 即使旧页面或接口直调丢弃失败券，提交入口仍拒绝部分代码。 */
    @Test
    public void submitShouldRejectOmittedRelatedMarket() {
        Object req = submitRequest("A.IB", "A.SZ");
        assertThatThrownBy(() -> validateInbound(req))
                .isInstanceOf(BizException.class).hasMessageContaining("整组提交").hasMessageContaining("A.SH");
        verify(markets).queryRelatedMarketCodes("A.IB");
    }

    /** 普通调库只提交有效市场，已退市的关联码保留在结果中 */
    @Test
    public void submitShouldRequireOnlyCurrentInboundMarkets() {
        // 详情仍包含沪市，但它已退市，服务端按实际主档判断
        SecurityInfoBo delisted = security("A.SH");
        delisted.setSecurityStatus("D");
        stub("querySecurityBoByCode", delisted, "A.SH");
        validateInbound(submitRequest("A.IB", "A.SZ"));
        assertThatThrownBy(() -> validateInbound(submitRequest("A.IB")))
                .isInstanceOf(BizException.class).hasMessageContaining("A.SZ");
        assertThatThrownBy(() -> validateInbound(submitRequest("A.IB", "A.SH", "A.SZ")))
                .isInstanceOf(BizException.class).hasMessageContaining("A.SH").hasMessageContaining("重新校验");
    }

    /** 送审清单中的有效码在终审前摘牌时，整组须重新校验 */
    @Test
    public void approvalShouldRejectMarketExpiredAfterSubmission() {
        // 准备有效目标池及送审后才过期的标的主档
        pool.setStatus("enabled");
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        when(investmentPoolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool));
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        SecurityInfoBo expired = security("A.SZ");
        expired.setDelistDate("20000101");
        stub("querySecurityBoByCode", expired, "A.SZ");
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode("A.SZ");
        log.setTargetPoolId(4L);
        log.setAdjustMode("调入");
        List<IpAdjustLogBo> logs = Collections.singletonList(log);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "recheckBeforeFinalApproval", logs))
                .isInstanceOf(BizException.class).hasMessageContaining("A.SZ").hasMessageContaining("重新校验");
    }

    /** 四池之外的目标池也进入证券主档检查 */
    @Test
    public void checkShouldReachSecurityLookupForPoolOutsideAbsQueryRange() {
        Object req = abs ? new ForbiddenAbsPoolAdjustCheckReq() : new AdjustCheckReq();
        Object item = abs ? new ForbiddenAbsPoolAdjustCheckReq.CheckItem() : new AdjustCheckReq.CheckItem();
        ReflectionTestUtils.setField(req, "securityCode", "A.IB");
        ReflectionTestUtils.setField(req, "items", Collections.singletonList(item));
        ReflectionTestUtils.setField(item, "targetPoolId", pool.getId());
        ReflectionTestUtils.setField(item, "adjustMode", "调入");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "checkAdjust", req))
                .isInstanceOf(BizException.class).hasMessageContaining("证券不存在");
    }

    /** 完整同池调入请求可通过入参及关联券完整性校验 */
    @Test
    public void submitShouldAcceptCompleteRelatedMarkets() {
        Object req = submitRequest("A.IB", "A.SH", "A.SZ");
        ReflectionTestUtils.invokeMethod(service, "validateSubmitReq", req);
        validateInbound(req);
    }

    /** 其他池的同券代码不能冒充本次目标池缺失的关联项。 */
    @Test
    public void submitShouldRequireRelatedCodesInSamePool() {
        Object req = submitRequest("A.IB", "A.SH", "A.SZ");
        List<?> items = (List<?>) ReflectionTestUtils.getField(req, "items");
        ReflectionTestUtils.setField(items.get(1), "targetPoolId", 8L);
        assertThatThrownBy(() -> validateInbound(req))
                .isInstanceOf(BizException.class).hasMessageContaining("A.SH");
    }

    /** 用已加载主档运行各链路的单笔调入复核 */
    private void validateInbound(Object req) {
        if (abs) {
            ReflectionTestUtils.invokeMethod(service, "validateRelatedInboundItemsOnSubmit", req, shared.getSecurityInfo());
        } else {
            ReflectionTestUtils.invokeMethod(service, "validateRelatedInboundItemsOnSubmit",
                    req, shared.getSecurityInfo(), null);
        }
    }

    /** 使用单券入口的既有展开阶段，关联券执行真实业务规则。 */
    private List<AdjustCheckDto.CheckResultItem> expand() {
        List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
        items.add(manual(4L));
        // 展开两个关联市场并执行真实校验
        expand(items);
        return items;
    }

    /** 将相同场景请求交给各链路的扩批校验。 */
    private void expand(List<AdjustCheckDto.CheckResultItem> items) {
        Object req = abs ? new ForbiddenAbsPoolAdjustCheckReq() : new AdjustCheckReq();
        ReflectionTestUtils.setField(req, "securityCode", "A.IB");
        ReflectionTestUtils.invokeMethod(service, "expandRelatedMarketCodeItems", req, shared, items);
    }

    /** 为不同 Mapper 的同构接口配置同一份场景数据。 */
    private <T> void stub(String method, T result, Object... args) {
        when(ReflectionTestUtils.<T>invokeMethod(mapper, method, args)).thenReturn(result);
    }

    /** 构造已通过前置主券校验的手工项。 */
    private AdjustCheckDto.CheckResultItem manual(Long poolId) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setTargetPoolId(poolId);
        item.setPoolName("三级库");
        item.setPoolType("credit_bond");
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_" + poolId);
        item.setCanAdjust(true);
        item.setFailReasons(Collections.emptyList());
        return item;
    }

    /** 构造独立主档，内评分缺失用于复现放开矩阵场景。 */
    private SecurityInfoBo security(String code) {
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode(code);
        security.setShortName(code);
        security.setSecurityType("mtn");
        return security;
    }

    /** 构造同券关联代码。 */
    private SecurityMarketDto market(String code) {
        SecurityMarketDto market = new SecurityMarketDto();
        market.setWindCode(code);
        return market;
    }

    /** 按实际链路构造同池调入请求，避免共享不同请求类型。 */
    private Object submitRequest(String... codes) {
        Object req = abs ? new ForbiddenAbsPoolAdjustSubmitReq() : new SecurityPoolAdjustSubmitReq();
        ReflectionTestUtils.setField(req, "securityCode", "A.IB");
        // 沿用页面基本信息已查询的关联集合，提交检查不再查库
        ReflectionTestUtils.setField(req, "relatedMarketCodes", Arrays.asList(market("A.SH"), market("A.SZ")));
        List<Object> items = new ArrayList<>();
        for (String code : codes) {
            Object item = abs ? new ForbiddenAbsPoolAdjustSubmitReq.AdjustItem() : new SecurityPoolAdjustSubmitReq.AdjustItem();
            ReflectionTestUtils.setField(item, "securityCode", code);
            ReflectionTestUtils.setField(item, "targetPoolId", 4L);
            ReflectionTestUtils.setField(item, "adjustMode", "调入");
            items.add(item);
        }
        ReflectionTestUtils.setField(req, "items", items);
        return req;
    }
}

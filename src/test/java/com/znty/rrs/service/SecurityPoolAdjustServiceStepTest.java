package com.znty.rrs.service;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;
import com.znty.rrs.common.enums.RelationType;

import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SecurityPoolAdjustServiceStepTest 测试类。 */
public class SecurityPoolAdjustServiceStepTest {

    /** 验证调库记录应返回流程名称。 */
    @Test
    public void queryAdjustLogListShouldReturnFlowName() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolService investmentPoolService = mock(InvestmentPoolService.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        ReflectionTestUtils.setField(service, "investmentPoolService", investmentPoolService);

        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(10L);
        log.setTargetPoolId(3L);
        log.setFlowName("信用债入库审批流程");
        when(mapper.queryAdjustLogList("110010123", "BATCH001")).thenReturn(Collections.singletonList(log));
        when(investmentPoolService.queryPoolFullNameMap()).thenReturn(Collections.singletonMap(3L, "信用债大库/二级库"));

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode("110010123");
        req.setAdjustBatchNo("BATCH001");

        List<AdjustLogDto> result = service.queryAdjustLogList(req);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlowName()).isEqualTo("信用债入库审批流程");
    }

    /** 验证 checkInConditionsShouldShowPendingProcessNodeLabel 测试场景。 */
    @Test
    public void checkInConditionsShouldShowPendingProcessNodeLabel() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(new SecurityInfoBo());
        ctx.setHasPendingProcess(true);
        ctx.setPendingProcessNodeLabel("研究员B复核");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>emptyMap());

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("证券存在进行中的调库流程（当前节点：研究员B复核），请等待流程结束后再发起调库");
    }

    /** 验证目标池已锁定时调入校验应失败。 */
    @Test
    public void checkInConditionsShouldFailWhenPoolLocked() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        // 构建已锁定目标池
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setLockFlag(1);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(new SecurityInfoBo());
        ctx.setTargetPool(pool);
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该池已经锁定，不能调入");
    }

    /** 验证目标池配置冻结期且证券仍在冻结期内时调出校验应失败。 */
    @Test
    public void checkOutConditionsShouldFailWhenFrozen() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        // 构建配置30天冻结期的目标池
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setFrozenPeriodIn(30);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(new SecurityInfoBo());
        ctx.setTargetPool(pool);
        // 证券当前在目标池中（调出前置）
        Set<Long> currentPoolIds = new HashSet<>();
        currentPoolIds.add(1L);
        ctx.setCurrentPoolIds(currentPoolIds);
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));
        // 入池时间为当前时间，仍在30天冻结期内
        ctx.setTargetPoolEntryTime(new java.util.Date());

        List<String> failures = service.checkOutConditions(ctx);

        assertThat(failures).contains("该证券还在投资池冻结期");
    }

    /** 验证证券品种不在目标池配置的投资品种内时调入校验应失败。 */
    @Test
    public void checkInConditionsShouldFailWhenVarietyMismatch() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        // 目标池仅允许 bond，证券品种为 fund
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setPoolName("信用债大库");
        pool.setVarietyCodes("[\"bond\"]");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setSecurityType("fund_type");
        when(mapper.queryCategoryTypeBySecurityType("fund_type")).thenReturn("fund");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该证券不在[信用债大库]所设定的投资品种内");
    }

    /** 验证证券市场不在目标池配置的投资市场内时调入校验应失败。 */
    @Test
    public void checkInConditionsShouldFailWhenMarketMismatch() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        // 目标池仅允许 SSE（沪市），证券仅在深市
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setPoolName("沪市池");
        pool.setMarketCodes("[\"SSE\"]");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setWindCodeSz("123.SZ");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该证券不在[沪市池]所设定的投资市场内");
    }

    /** 验证目标池来源池限制可由本次同批调入来源池满足。 */
    @Test
    public void checkInConditionsShouldPassWhenSourcePoolIncludedInRequest() {
        SecurityPoolAdjustService service = buildServiceWithMapper();
        AdjustCheckContext ctx = buildSourcePoolContext();
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(new HashSet<Long>(Collections.singletonList(2L)));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
    }

    /** 验证目标池来源池限制未被当前池或本次请求满足时应失败。 */
    @Test
    public void checkInConditionsShouldFailWhenSourcePoolNotSatisfied() {
        SecurityPoolAdjustService service = buildServiceWithMapper();
        AdjustCheckContext ctx = buildSourcePoolContext();
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("目标池配置了来源池限制，证券须先在以下池中：信用债大库/一级库");
    }

    /** 验证证券当前已在来源池时来源池限制仍按原逻辑通过。 */
    @Test
    public void checkInConditionsShouldPassWhenSecurityAlreadyInSourcePool() {
        SecurityPoolAdjustService service = buildServiceWithMapper();
        AdjustCheckContext ctx = buildSourcePoolContext();
        ctx.setCurrentPoolIds(new HashSet<Long>(Collections.singletonList(2L)));
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
    }

    /** 构建仅用于来源池限制校验的服务实例。 */
    private SecurityPoolAdjustService buildServiceWithMapper() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mock(SecurityPoolAdjustMapper.class));
        return service;
    }

    /** 构建配置来源池限制的调库校验上下文。 */
    private AdjustCheckContext buildSourcePoolContext() {
        InvestmentPoolBo targetPool = new InvestmentPoolBo();
        targetPool.setId(1L);
        targetPool.setPoolName("信用债大库/二级库");
        InvestmentPoolBo sourcePool = new InvestmentPoolBo();
        sourcePool.setId(2L);
        sourcePool.setPoolName("信用债大库/一级库");
        Map<String, List<Long>> relations = new HashMap<>();
        relations.put(RelationType.SOURCE.getCode(), Collections.singletonList(2L));
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(1L, targetPool);
        poolMap.put(2L, sourcePool);
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setWindCode("110010123");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(targetPool);
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(relations);
        ctx.setPoolMap(poolMap);
        return ctx;
    }

    /** 验证债券已到期时调入校验应失败（类型特有 checkBondIn）。 */
    @Test
    public void checkInConditionsShouldFailWhenBondMaturity() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setMaturityDate("2020-01-01");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("bond");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该债券已经到期，无法调入");
    }

    /** 验证股票已退市时调入校验应失败（类型特有 checkStockIn）。 */
    @Test
    public void checkInConditionsShouldFailWhenStockDelist() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDelistDate("2020-01-01");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("stock");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该股票已经退市，无法调入");
    }

    /** 验证证券在全局禁投池时调入校验应失败。 */
    @Test
    public void checkInConditionsShouldFailWhenInForbiddenPool() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setWindCode("110010123");
        when(mapper.querySecurityInForbiddenPool("110010123")).thenReturn(true);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).contains("该证券在禁止池中，不能调入");
    }

    /** 验证债券不应被股票入池评级限制误拦截。 */
    @Test
    public void checkInConditionsShouldNotFailBondWhenGradeAstrictConfigured() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setGradeAstrict("1,2");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setWindCode("110010123");
        sec.setRatingBond("A");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("bond");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
    }

    /** 验证股票评级来源未接入时，股票入池评级限制暂不强拦。 */
    @Test
    public void checkInConditionsShouldSkipStockGradeAstrictWhenRatingSourceMissing() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setGradeAstrict("1,2");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setWindCode("600000.SH");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setCategoryType("stock");
        ctx.setCurrentPoolIds(Collections.<Long>emptySet());
        ctx.setRequestInPoolIds(Collections.<Long>emptySet());
        ctx.setRequestOutPoolIds(Collections.<Long>emptySet());
        ctx.setTargetPoolRelations(Collections.<String, List<Long>>emptyMap());
        ctx.setPoolMap(Collections.<Long, InvestmentPoolBo>singletonMap(1L, pool));

        List<String> failures = service.checkInConditions(ctx);

        assertThat(failures).isEmpty();
    }

    /** 行业限制校验：证券行业与池配置不符时应返回失败原因。 */
    @Test
    public void inCheckIndustryShouldFailWhenMismatch() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setIndustryCode("制造业");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIndustryName("金融业");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckIndustry", ctx);
        assertThat(failure).isEqualTo("请选择正确的行业;");
    }

    /** 行业限制校验：证券行业匹配时应通过。 */
    @Test
    public void inCheckIndustryShouldPassWhenMatch() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setIndustryCode("制造业");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIndustryName("制造业");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckIndustry", ctx);
        assertThat(failure).isNull();
    }

    /** 行业限制校验：industry_exponent!=0（行业指数模式）时应跳过。 */
    @Test
    public void inCheckIndustryShouldSkipWhenExponentNonZero() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setIndustryCode("制造业");
        pool.setIndustryExponent(1);
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIndustryName("金融业");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckIndustry", ctx);
        assertThat(failure).isNull();
    }

    /** 基金评分校验：未传 fundRate 时应返回失败原因。 */
    @Test
    public void inCheckFundRateShouldFailWhenFundRateMissing() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setPoolName("基金池");
        pool.setFundRateLimit("3<=#rate<=8");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        // 未传 fundRate
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckFundRate", ctx);
        assertThat(failure).isEqualTo("基金池的评分，必须在3<=基金评分<=8");
    }

    /** 基金评分校验：fundRate 在范围内时应通过。 */
    @Test
    public void inCheckFundRateShouldPassWhenWithinRange() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setPoolName("基金池");
        pool.setFundRateLimit("3<=#rate<=8");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setFundRate("5");
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckFundRate", ctx);
        assertThat(failure).isNull();
    }

    /** 基金评分校验：fundRate 超过上限时应返回失败原因。 */
    @Test
    public void inCheckFundRateShouldFailWhenAboveUpper() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setPoolName("基金池");
        pool.setFundRateLimit("3<=#rate<=8");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setFundRate("10");
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckFundRate", ctx);
        assertThat(failure).isEqualTo("基金池的评分，必须在3<=基金评分<=8");
    }

    /** 基金评分校验：池未配置 fund_rate_limit 时应跳过。 */
    @Test
    public void inCheckFundRateShouldSkipWhenNoLimit() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setPoolName("基金池");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setFundRate("5");
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckFundRate", ctx);
        assertThat(failure).isNull();
    }

    /** 开放日校验：启用开放日且当日不在开放区间时应返回失败原因。 */
    @Test
    public void inCheckOpenDayShouldFailWhenNotInOpenDay() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setOpenDayAdjust(1);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        // 当日不在开放区间
        when(mapper.queryPoolInOpenDay(any(Long.class), any(String.class))).thenReturn(false);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckOpenDay", ctx);
        assertThat(failure).isEqualTo("不在开放日内，不能调入;");
    }

    /** 开放日校验：启用开放日且当日在开放区间时应通过。 */
    @Test
    public void inCheckOpenDayShouldPassWhenInOpenDay() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        pool.setOpenDayAdjust(1);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        // 当日在开放区间
        when(mapper.queryPoolInOpenDay(any(Long.class), any(String.class))).thenReturn(true);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckOpenDay", ctx);
        assertThat(failure).isNull();
    }

    /** 开放日校验：未启用开放日（open_day_adjust 空/0）时应跳过，不查开放日表。 */
    @Test
    public void inCheckOpenDayShouldSkipWhenNotEnabled() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(1L);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckOpenDay", ctx);
        assertThat(failure).isNull();
        verify(mapper, never()).queryPoolInOpenDay(any(Long.class), any(String.class));
    }

    /** 主体债入库规则：目标池在矩阵允许列表内时应通过。 */
    @Test
    public void inCheckMainGradeRuleShouldPassWhenTargetPoolAllowed() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        CreditBondGradeRuleMapper gradeRuleMapper = mock(CreditBondGradeRuleMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", gradeRuleMapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(2L);
        pool.setPoolType("credit_bond");
        pool.setPoolName("一级库");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setSecurityType("corporate_bond");
        sec.setInnerIssuerRating("1");
        sec.setTermYear(new java.math.BigDecimal("6"));
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        ctx.setPoolMap(Collections.singletonMap(2L, pool));
        // 期限档 GT_5：term_year > 5
        CreditBondTermBucketBo gt5 = new CreditBondTermBucketBo();
        gt5.setBucketCode("GT_5");
        gt5.setMinTermYear(new java.math.BigDecimal("5"));
        gt5.setMinInclusive(0);
        when(gradeRuleMapper.queryEnabledTermBucketList()).thenReturn(Collections.singletonList(gt5));
        when(gradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(any(String.class), any(String.class)))
                .thenReturn(Collections.singletonList(2L));
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckMainGradeRule", ctx);
        assertThat(failure).isNull();
    }

    /** 主体债入库规则：目标池不在矩阵允许列表时应返回失败原因。 */
    @Test
    public void inCheckMainGradeRuleShouldFailWhenTargetPoolNotAllowed() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        CreditBondGradeRuleMapper gradeRuleMapper = mock(CreditBondGradeRuleMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", gradeRuleMapper);
        InvestmentPoolBo targetPool = new InvestmentPoolBo();
        targetPool.setId(5L);
        targetPool.setPoolType("credit_bond");
        targetPool.setPoolName("五级库");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setSecurityType("corporate_bond");
        sec.setInnerIssuerRating("4");
        sec.setTermYear(new java.math.BigDecimal("6"));
        // 池名映射（允许池 2/3 + 目标池 5）
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        InvestmentPoolBo p2 = new InvestmentPoolBo(); p2.setId(2L); p2.setPoolName("一级库"); poolMap.put(2L, p2);
        InvestmentPoolBo p3 = new InvestmentPoolBo(); p3.setId(3L); p3.setPoolName("二级库"); poolMap.put(3L, p3);
        poolMap.put(5L, targetPool);
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(targetPool);
        ctx.setSecurityInfo(sec);
        ctx.setPoolMap(poolMap);
        CreditBondTermBucketBo gt5 = new CreditBondTermBucketBo();
        gt5.setBucketCode("GT_5");
        gt5.setMinTermYear(new java.math.BigDecimal("5"));
        gt5.setMinInclusive(0);
        when(gradeRuleMapper.queryEnabledTermBucketList()).thenReturn(Collections.singletonList(gt5));
        // 矩阵允许一/二级库，不含五级库
        when(gradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(any(String.class), any(String.class)))
                .thenReturn(Arrays.asList(2L, 3L));
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckMainGradeRule", ctx);
        assertThat(failure).isEqualTo("该债券只能调入以下池：一级库、二级库");
    }

    /** 主体债入库规则：未配置主体内评分档时应返回失败原因。 */
    @Test
    public void inCheckMainGradeRuleShouldFailWhenNoGrade() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        CreditBondGradeRuleMapper gradeRuleMapper = mock(CreditBondGradeRuleMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", gradeRuleMapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(2L);
        pool.setPoolType("credit_bond");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setSecurityType("corporate_bond");
        // 未配置主体内评分档
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckMainGradeRule", ctx);
        assertThat(failure).isEqualTo("未配置主体内评分档，不符合入库条件");
    }

    /** 主体债入库规则：目标池非信用债大库时应跳过。 */
    @Test
    public void inCheckMainGradeRuleShouldSkipWhenNotCreditBondPool() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        CreditBondGradeRuleMapper gradeRuleMapper = mock(CreditBondGradeRuleMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", gradeRuleMapper);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(2L);
        pool.setPoolType("special_account");
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInnerIssuerRating("1");
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setTargetPool(pool);
        ctx.setSecurityInfo(sec);
        String failure = ReflectionTestUtils.invokeMethod(service, "inCheckMainGradeRule", ctx);
        assertThat(failure).isNull();
        verify(gradeRuleMapper, never()).queryAllowedPoolIdsByGradeAndBucket(any(String.class), any(String.class));
    }

    /** 验证 queryAdjustPoolListShouldSkipPermissionFilterForAdmin 测试场景。 */
    @Test
    public void queryAdjustPoolListShouldSkipPermissionFilterForAdmin() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        when(mapper.queryPoolList()).thenReturn(Arrays.asList(
                // 构建投资池测试数据
                buildPool(1L, null, "根库"),
                // 构建投资池测试数据
                buildPool(2L, 1L, "一级库")));
        when(mapper.queryMutexRelationList()).thenReturn(Collections.emptyList());
        PoolDto poolCount = new PoolDto();
        poolCount.setId(2L);
        poolCount.setCurrentCount(3);
        when(adjustMapper.queryPoolCurrentCountList()).thenReturn(Collections.singletonList(poolCount));

        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setCurrentUserId("1");

        List<PoolDto> result = service.queryAdjustPoolList(req);

        assertThat(result).extracting(PoolDto::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(PoolDto::getCurrentCount).containsExactly(0, 3);
        verify(mapper, never()).queryPermissionListByType(org.mockito.Matchers.anyString());
        verify(mapper, never()).queryUserRoleIdList(org.mockito.Matchers.anyLong());
    }

    /** 验证 filterAdjustablePoolsByUserShouldKeepAncestors 测试场景。 */
    @Test
    public void filterAdjustablePoolsByUserShouldKeepAncestors() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        List<InvestmentPoolBo> pools = Arrays.asList(
                // 构建投资池测试数据
                buildPool(1L, null, "根库"),
                // 构建投资池测试数据
                buildPool(2L, 1L, "信用债"),
                // 构建投资池测试数据
                buildPool(3L, 2L, "一级库"),
                // 构建投资池测试数据
                buildPool(4L, 1L, "专户产品"),
                // 构建投资池测试数据
                buildPool(5L, 4L, "二级库"),
                // 构建投资池测试数据
                buildPool(6L, 1L, "未授权库"));
        when(mapper.queryUserRoleIdList(2001L)).thenReturn(Collections.singletonList(20L));
        when(mapper.queryPermissionListByType("adjustable")).thenReturn(Arrays.asList(
                // 构建投资池权限测试数据
                buildPermission(3L, "user", 2001L),
                // 构建投资池权限测试数据
                buildPermission(5L, "role", 20L),
                // 构建投资池权限测试数据
                buildPermission(6L, "user", 3001L)));

        List<InvestmentPoolBo> result = ReflectionTestUtils.invokeMethod(service, "filterAdjustablePoolsByUser", pools, 2001L);

        assertThat(result).extracting(InvestmentPoolBo::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    /** 验证 createInitialStepsShouldCreatePendingStepForEachPreemptHandler 测试场景。 */
    @Test
    public void createInitialStepsShouldCreatePendingStepForEachPreemptHandler() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo approval = buildNode(2L, "approval", "approval", 2);
        // 构建审批配置测试数据
        NodeApprovalConfigBo config = buildConfig(20L, approval.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, approval),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), approval.getId())),
                Arrays.asList(config),
                // 构建审批处理人映射测试数据
                buildHandlerMap(config.getId(), 5));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(6)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getNodeType()).isEqualTo("start");
        assertThat(steps.get(0).getStepStatus()).isEqualTo("auto_process");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getApprovalStrategy)
                .containsOnly("preempt");
        assertThat(steps.subList(1, 6)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2", "3", "4", "5");
    }

    /** 验证 createInitialStepsShouldApproveInitiatorThenCreateConfiguredPendingSteps 测试场景。 */
    @Test
    public void createInitialStepsShouldApproveInitiatorThenCreateConfiguredPendingSteps() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "start", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo initiator = buildNode(2L, "initiator", "approval", 2);
        // 构建流程节点测试数据
        FlowNodeBo approval = buildNode(3L, "approval", "approval", 3);
        // 构建审批配置测试数据
        NodeApprovalConfigBo initiatorConfig = buildConfig(20L, initiator.getId(), "initiator");
        // 构建审批配置测试数据
        NodeApprovalConfigBo approvalConfig = buildConfig(30L, approval.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, initiator, approval),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), initiator.getId()),
                        buildEdge(initiator.getId(), approval.getId(), "submit")),
                Arrays.asList(initiatorConfig, approvalConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(approvalConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(initiator.getId());
        assertThat(steps.get(1).getApprovalStrategy()).isEqualTo("initiator");
        assertThat(steps.get(1).getStepStatus()).isEqualTo("submit");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    /** 验证 createInitialStepsShouldSkipSubmitterNodeEvenWhenOldConfigIsPreempt 测试场景。 */
    @Test
    public void createInitialStepsShouldSkipSubmitterNodeWhenInitiatorStrategy() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        // 构建流程节点测试数据
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        // 构建审批配置测试数据
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "initiator");
        // 构建审批配置测试数据
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                // 构建流程连线测试数据
                Arrays.asList(buildEdge(start.getId(), submitter.getId()),
                        buildEdge(submitter.getId(), reviewer.getId(), "submit")),
                Arrays.asList(submitterConfig, reviewerConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(reviewerConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(4)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();

        assertThat(steps.get(0).getFlowNodeId()).isEqualTo(start.getId());
        assertThat(steps.get(0).getHandlerId()).isNull();
        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(submitter.getId());
        assertThat(steps.get(1).getStepStatus()).isEqualTo("submit");
        assertThat(steps.get(1).getHandlerId()).isEqualTo("1");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getFlowNodeId)
                .containsOnly(reviewer.getId());
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getStepStatus)
                .containsOnly("pending");
        assertThat(steps.subList(2, 4)).extracting(IpAdjustStepBo::getHandlerId)
                .containsExactly("1", "2");
    }

    /** 验证 initiator 节点缺少 submit 出边时不作为发起节点自动处理。 */
    @Test
    public void createInitialStepsShouldCreatePendingWhenInitiatorHasNoSubmitRoute() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);

        // 构建流程节点测试数据
        FlowNodeBo start = buildNode(1L, "n1", "start", 1);
        // 构建流程节点测试数据
        FlowNodeBo submitter = buildNode(2L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        submitter.setSubLabel("researcher-a");
        // 构建流程节点测试数据
        FlowNodeBo reviewer = buildNode(3L, "n3", "approval", 3);
        reviewer.setLabel("研究员B复核");
        // 构建审批配置测试数据
        NodeApprovalConfigBo submitterConfig = buildConfig(20L, submitter.getId(), "initiator");
        // 构建审批配置测试数据
        NodeApprovalConfigBo reviewerConfig = buildConfig(30L, reviewer.getId(), "preempt");
        // 构建流程快照测试数据
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, reviewer),
                // 构建流程连线测试数据
                Collections.singletonList(buildEdge(start.getId(), submitter.getId())),
                Arrays.asList(submitterConfig, reviewerConfig),
                // 构建审批处理人映射测试数据
                buildHandlerMap(reviewerConfig.getId(), 2));

        ReflectionTestUtils.invokeMethod(service, "createInitialSteps", 100L, null, snapshot, "1", "admin");

        ArgumentCaptor<IpAdjustStepBo> captor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(captor.capture());
        List<IpAdjustStepBo> steps = captor.getAllValues();
        assertThat(steps.get(0).getNodeType()).isEqualTo("start");
        assertThat(steps.get(1).getFlowNodeId()).isEqualTo(submitter.getId());
        assertThat(steps.get(1).getStepStatus()).isEqualTo("pending");
    }

    /** 验证 executeInboundSubmitShouldTreatMissingFlowAsDirect 测试场景。 */
    @Test
    public void executeInboundSubmitShouldTreatMissingFlowAsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("\u8c03\u5165");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调入");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        List<Long> result = ReflectionTestUtils.invokeMethod(
                service, "executeInboundSubmit", req, shared, null, directApplyLogs);

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(logCaptor.getValue().getAdjustBatchNo()).matches("BOND\\d{17}3001");
        assertThat(logCaptor.getValue().getAdjustBatchNo()).endsWith("3001");
        assertThat(directApplyLogs).containsExactly(logCaptor.getValue());
        assertThat(result).containsExactly(99L);
    }

    /** 验证 executeOutboundSubmitShouldCreateLogWhenFlowIsDirect 测试场景。 */
    @Test
    public void executeOutboundSubmitShouldCreateLogWhenFlowIsDirect() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(88L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("\u8c03\u51fa");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调出");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();

        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        List<Long> result = ReflectionTestUtils.invokeMethod(
                service, "executeOutboundSubmit", req, shared, null, directApplyLogs);

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(captor.getValue().getAdjustBatchNo()).endsWith("3001");
        assertThat(directApplyLogs).containsExactly(captor.getValue());
        assertThat(result).containsExactly(88L);
    }

    /** 目标池为空时报告必填校验应跳过。 */
    @Test
    public void checkReportRequiredShouldSkipWhenPoolNull() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        // 池为空时直接跳过，不校验报告
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, null, "any", "");
    }

    /** 限制为 none 时报告必填校验应通过。 */
    @Test
    public void checkReportRequiredShouldPassWhenNone() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        // none=不限制，无报告也应通过
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "none", "");
    }

    /** 限制为 any 且无报告时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenAnyAndNoReport() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", "");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("any 限制且无报告时应抛出异常");
    }

    /** 限制为 any 且已上传报告文件时应通过。 */
    @Test
    public void checkReportRequiredShouldPassWhenAnyAndHasFileReport() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        // any 限制，已上传报告文件，应通过
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "any", "");
    }

    /** 限制为 internal 且仅有上传文件（非内部报告库）时应抛出异常。 */
    @Test
    public void checkReportRequiredShouldFailWhenInternalAndOnlyFile() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        try {
            ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", "");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求内部研究报告");
            return;
        }
        throw new AssertionError("internal 限制且仅上传文件时应抛出异常");
    }

    /** 限制为 internal 且选择了内部报告库附件时应通过。 */
    @Test
    public void checkReportRequiredShouldPassWhenInternalAndHasSourceAttachment() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(1L));
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        // internal 限制，已从内部报告库选择附件，应通过
        ReflectionTestUtils.invokeMethod(service, "checkReportRequired", item, pool, "internal", "");
        verify(attachmentService).validateCreditReportSources(Collections.singletonList(1L), true);
    }

    /** 调入提交应按目标池 in_report_restriction 校验报告。 */
    @Test
    public void executeInboundSubmitShouldCheckInReportRestriction() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调入");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("报告池");
        item.setPoolType("credit_bond");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调入");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        // 仅配置 in_report_restriction=any，out_report_restriction 留空，区分调入/调出取值
        pool.setInReportRestriction("any");
        ReflectionTestUtils.setField(shared, "poolMap", Collections.singletonMap(10L, pool));

        try {
            ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared, null,
                    new ArrayList<IpAdjustLogBo>());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("调入目标池要求报告时应抛出异常");
    }

    /** 调出提交应按目标池 out_report_restriction 校验报告。 */
    @Test
    public void executeOutboundSubmitShouldCheckOutReportRestriction() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调出");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("报告池");
        item.setPoolType("credit_bond");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调出");
        req.setItems(Collections.singletonList(item));

        // 构建调库提交共享数据测试数据
        Object shared = buildSubmitSharedData();
        InvestmentPoolBo pool = buildPool(10L, null, "报告池");
        // 仅配置 out_report_restriction=any，in_report_restriction 留空，区分调入/调出取值
        pool.setOutReportRestriction("any");
        ReflectionTestUtils.setField(shared, "poolMap", Collections.singletonMap(10L, pool));

        try {
            ReflectionTestUtils.invokeMethod(service, "executeOutboundSubmit", req, shared, null,
                    new ArrayList<IpAdjustLogBo>());
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("要求研究报告");
            return;
        }
        throw new AssertionError("调出目标池要求报告时应抛出异常");
    }

    /** 验证共享批次号上下文下连续提交应递增批次号。 */
    @Test
    public void executeInboundSubmitShouldIncreaseBatchNoWithSharedContext() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(99L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        Object batchNoContext = buildBatchNoContext();
        SecurityPoolAdjustSubmitReq firstReq = buildDirectInboundSubmitReq("S001", "S001_manual_10_调入");
        SecurityPoolAdjustSubmitReq secondReq = buildDirectInboundSubmitReq("S002", "S002_manual_10_调入");

        Object firstShared = buildSubmitSharedData(batchNoContext);
        Object secondShared = buildSubmitSharedData(batchNoContext);

        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", firstReq, firstShared, null,
                new ArrayList<IpAdjustLogBo>());
        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", secondReq, secondShared, null,
                new ArrayList<IpAdjustLogBo>());

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(IpAdjustLogBo::getAdjustBatchNo)
                .doesNotHaveDuplicates();
        assertThat(logCaptor.getAllValues().get(0).getAdjustBatchNo()).matches("BOND\\d{17}3001");
        assertThat(logCaptor.getAllValues().get(1).getAdjustBatchNo()).matches("BOND\\d{17}3002");
        assertThat(logCaptor.getAllValues().get(0).getAdjustBatchNo()).endsWith("3001");
        assertThat(logCaptor.getAllValues().get(1).getAdjustBatchNo()).endsWith("3002");
    }

    /** 验证普通提交应同时绑定本地上传附件和报告库来源附件。 */
    @Test
    public void bindSubmitAttachmentsShouldBindUploadedFilesAndCopyReportSources() {
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        item.setMaterialFileIndexes(Collections.singletonList(1));
        item.setCreditReportSourceAttachmentIds(Collections.singletonList(7L));
        item.setMaterialSourceAttachmentIds(Collections.singletonList(8L));

        ReflectionTestUtils.invokeMethod(service, "bindSubmitAttachments", 88L, item, null, "1");

        verify(attachmentService).bindAttachments(88L, Collections.singletonList(0),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), null);
        verify(attachmentService).bindAttachments(88L, Collections.singletonList(1),
                AttachmentCategory.MATERIAL_HAND.getCode(), null);
        verify(attachmentService).copyReportAttachments(88L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1");
        verify(attachmentService).copyReportAttachments(88L, Collections.singletonList(8L),
                AttachmentPurpose.MATERIAL.getCode(), "1");
    }

    /** 验证白名单直通流程应记录调入批次号及流程步骤。 */
    @Test
    public void executeInboundSubmitShouldCreateStepsWithBatchNoForWhitelistFlow() throws Exception {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", mock(SysAttachmentService.class));
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(106L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));

        FlowNodeBo start = buildNode(10601L, "n1", "start", 1);
        FlowNodeBo submitter = buildNode(10602L, "n2", "approval", 2);
        submitter.setLabel("研究员A发起");
        FlowNodeBo end = buildNode(10603L, "n3", "end", 3);
        NodeApprovalConfigBo submitterConfig = buildConfig(106L, submitter.getId(), "initiator");
        Object snapshot = buildSnapshot(
                Arrays.asList(start, submitter, end),
                Arrays.asList(buildEdge(start.getId(), submitter.getId()),
                        buildEdge(submitter.getId(), end.getId(), "submit")),
                Collections.singletonList(submitterConfig),
                Collections.<Long, List<NodeApprovalHandlerBo>>emptyMap());
        Map<Long, Object> snapshotMap = new HashMap<>();
        snapshotMap.put(106L, snapshot);

        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode("S001");
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调入");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("credit_bond");
        item.setItemTag("manual");
        item.setAdjustGroupKey("manual_10_调入");
        item.setFlowId(106L);
        item.setFlowKey("bond:whitelist-inbound");
        item.setFlowType("whitelistInbound");
        req.setItems(Collections.singletonList(item));

        // 构建包含白名单流程快照的提交共享数据
        Object shared = buildSubmitSharedData(snapshotMap);
        ReflectionTestUtils.invokeMethod(service, "executeInboundSubmit", req, shared, null,
                new ArrayList<IpAdjustLogBo>());

        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        ArgumentCaptor<IpAdjustStepBo> stepCaptor = ArgumentCaptor.forClass(IpAdjustStepBo.class);
        verify(mapper).addAdjustLog(logCaptor.capture());
        verify(mapper, times(3)).addAdjustStep(stepCaptor.capture());
        String batchNo = logCaptor.getValue().getAdjustBatchNo();
        assertThat(batchNo).endsWith("1");
        assertThat(stepCaptor.getAllValues()).extracting(IpAdjustStepBo::getAdjustBatchNo)
                .containsOnly(batchNo);
    }

    /** 验证信用债在库调整应按上调或下调流程编码获取流程。 */
    @Test
    public void resolveAdjustFlowOptionsShouldQueryFlowByCreditBondAdjustDirection() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        FlowDefinitionBo upgradeFlow = buildFlowDefinition(101L, "bond:standard-upgrade", "债券标准升库流程");
        FlowDefinitionBo downgradeFlow = buildFlowDefinition(102L, "bond:standard-downgrade", "债券标准降库流程");
        when(flowMapper.queryActiveFlowByKey("bond:standard-upgrade")).thenReturn(upgradeFlow);
        when(flowMapper.queryActiveFlowByKey("bond:standard-downgrade")).thenReturn(downgradeFlow);

        // 校验目标池层级高于当前池时使用上调流程
        AdjustCheckDto.FlowOption upgradeOption = resolveCreditBondFlowOption(service, 1, 2);
        assertThat(upgradeOption.getFlowType()).isEqualTo("upgradeInbound");
        assertThat(upgradeOption.getFlowId()).isEqualTo(101L);
        assertThat(upgradeOption.getFlowKey()).isEqualTo("bond:standard-upgrade");

        // 校验目标池层级低于当前池时使用下调流程
        AdjustCheckDto.FlowOption downgradeOption = resolveCreditBondFlowOption(service, 3, 2);
        assertThat(downgradeOption.getFlowType()).isEqualTo("downgradeInbound");
        assertThat(downgradeOption.getFlowId()).isEqualTo(102L);
        assertThat(downgradeOption.getFlowKey()).isEqualTo("bond:standard-downgrade");
    }

    /** 验证当前已在目标池调入互斥池时应优先返回特殊审批流程。 */
    @Test
    public void resolveAdjustFlowOptionsShouldPreferSpecialInboundWhenCurrentPoolInMutex() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        FlowDefinitionBo specialFlow = buildFlowDefinition(108L, "bond:special-inbound", "债券特殊策略入库流程");
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound")).thenReturn(specialFlow);

        AdjustSharedData shared = buildSpecialInboundShared(2L, 3L, "信用债大库/一级库", "专户产品/二级库");
        InvestmentPoolBo targetPool = shared.getPoolMap().get(2L);
        targetPool.setPoolType("credit_bond");
        targetPool.setInnerSort(1);
        InvestmentPoolBo currentPool = shared.getPoolMap().get(3L);
        currentPool.setPoolType("credit_bond");
        currentPool.setInnerSort(3);

        AdjustCheckDto.CheckResultItem item = buildManualInboundItem(2L);
        List<AdjustCheckDto.FlowOption> options = ReflectionTestUtils.invokeMethod(
                service, "resolveAdjustFlowOptionsForItem", new AdjustCheckReq(), shared, item);

        assertThat(options).hasSize(1);
        AdjustCheckDto.FlowOption option = options.get(0);
        assertThat(option.getFlowType()).isEqualTo("specialInbound");
        assertThat(option.getFlowKey()).isEqualTo("bond:special-inbound");
        assertThat(option.getFlowId()).isEqualTo(108L);
        assertThat(option.isRecommended()).isTrue();
        assertThat(option.getMatchReasons().get(0)).contains("专户产品/二级库");
    }

    /** 验证特殊审批流程未启用时应回退原有流程选择。 */
    @Test
    public void resolveAdjustFlowOptionsShouldFallbackWhenSpecialInboundFlowMissing() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound")).thenReturn(null);

        AdjustSharedData shared = buildSpecialInboundShared(2L, 3L, "境外债库", "专户产品/二级库");
        InvestmentPoolBo targetPool = shared.getPoolMap().get(2L);
        targetPool.setPoolType("offshore_bond");
        targetPool.setInFlowId(105L);
        targetPool.setInFlowKey("bond:fast-inbound");
        targetPool.setInFlowName("债券快速入库流程");

        List<AdjustCheckDto.FlowOption> options = ReflectionTestUtils.invokeMethod(
                service, "resolveAdjustFlowOptionsForItem", new AdjustCheckReq(), shared, buildManualInboundItem(2L));

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getFlowType()).isEqualTo("normalInbound");
        assertThat(options.get(0).getFlowKey()).isEqualTo("bond:fast-inbound");
    }

    /** 验证批量调库链路命中特殊审批流程。 */
    @Test
    public void batchAdjustShouldResolveSpecialInboundFlow() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound"))
                .thenReturn(buildFlowDefinition(108L, "bond:special-inbound", "债券特殊策略入库流程"));

        AdjustCheckDto.FlowOption option = ReflectionTestUtils.invokeMethod(
                service, "resolveSpecialInboundFlowOption",
                buildPool(2L, 1L, "一级库"),
                buildSpecialInboundShared(2L, 3L, "一级库", "二级库"));

        assertThat(option.getFlowType()).isEqualTo("specialInbound");
        assertThat(option.getFlowKey()).isEqualTo("bond:special-inbound");
    }

    /** 验证禁投池调整链路命中特殊审批流程。 */
    @Test
    public void forbiddenAdjustShouldResolveSpecialInboundFlow() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound"))
                .thenReturn(buildFlowDefinition(108L, "bond:special-inbound", "债券特殊策略入库流程"));

        AdjustCheckDto.FlowOption option = ReflectionTestUtils.invokeMethod(
                service, "resolveSpecialInboundFlowOption",
                buildPool(15L, null, "禁投池"),
                buildSpecialInboundShared(15L, 16L, "禁投池", "观察池"));

        assertThat(option.getFlowType()).isEqualTo("specialInbound");
        assertThat(option.getFlowKey()).isEqualTo("bond:special-inbound");
    }

    /** 验证 CRMW 调库链路命中特殊审批流程。 */
    @Test
    public void crmwAdjustShouldResolveSpecialInboundFlow() {
        FlowMapper flowMapper = mock(FlowMapper.class);
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        when(flowMapper.queryActiveFlowByKey("bond:special-inbound"))
                .thenReturn(buildFlowDefinition(108L, "bond:special-inbound", "债券特殊策略入库流程"));

        com.znty.rrs.entity.crmwpooladjust.AdjustSharedData shared = buildCrmwSpecialInboundShared(
                19L, 20L, "CRMW核心库", "CRMW关注库");
        com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto.FlowOption option = ReflectionTestUtils.invokeMethod(
                service, "resolveSpecialInboundFlowOption",
                buildPool(19L, null, "CRMW核心库"), shared);

        assertThat(option.getFlowType()).isEqualTo("specialInbound");
        assertThat(option.getFlowKey()).isEqualTo("bond:special-inbound");
    }

    /** 验证手工调入失败时应同步阻断派生的互斥调出项。 */
    @Test
    public void executeInAdjustCheckShouldBlockMutexOutboundWhenManualInboundFails() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryPoolCurrentCount(any(Long.class))).thenReturn(1);

        InvestmentPoolBo rootPool = buildPool(1L, null, "信用债大库");
        InvestmentPoolBo currentPool = buildPool(2L, 1L, "二级库");
        InvestmentPoolBo targetPool = buildPool(3L, 1L, "三级库");
        InvestmentPoolBo forbiddenPool = buildPool(4L, null, "禁投池");
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(rootPool.getId(), rootPool);
        poolMap.put(currentPool.getId(), currentPool);
        poolMap.put(targetPool.getId(), targetPool);
        poolMap.put(forbiddenPool.getId(), forbiddenPool);

        Map<String, List<Long>> targetRelations = new HashMap<>();
        targetRelations.put("in_restrict", Collections.singletonList(forbiddenPool.getId()));
        targetRelations.put("in_mutex", Collections.singletonList(currentPool.getId()));
        Map<Long, Map<String, List<Long>>> relationMap = new HashMap<>();
        relationMap.put(targetPool.getId(), targetRelations);

        Set<Long> currentPoolIds = new HashSet<>(Arrays.asList(currentPool.getId(), forbiddenPool.getId()));
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(new SecurityInfoBo());
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(currentPoolIds);
        shared.setPoolRelationMap(relationMap);
        shared.setRequestInPoolIds(Collections.singleton(targetPool.getId()));
        shared.setRequestOutPoolIds(Collections.<Long>emptySet());

        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(targetPool.getId());
        item.setPoolType("credit_bond");
        item.setAdjustMode("调入");
        AdjustCheckReq req = new AdjustCheckReq();
        req.setItems(Collections.singletonList(item));
        Set<String> coveredKeys = new HashSet<>();
        coveredKeys.add(targetPool.getId() + "_调入");

        List<AdjustCheckDto.CheckResultItem> results = ReflectionTestUtils.invokeMethod(
                service, "executeInAdjustCheck", req, shared, coveredKeys);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isCanAdjust()).isFalse();
        assertThat(results.get(0).getFailReasons()).contains(
                "证券在调入限制池中，无法操作：禁投池");
        assertThat(results.get(1).getItemTag()).isEqualTo("mutex");
        assertThat(results.get(1).isCanAdjust()).isFalse();
        assertThat(results.get(1).getFailReasons().get(0))
                .isEqualTo("关联手工调入项“信用债大库/三级库”校验未通过");
    }

    /** 构造信用债在库调整场景并获取流程候选项。 */
    private AdjustCheckDto.FlowOption resolveCreditBondFlowOption(
            SecurityPoolAdjustService service, int targetSort, int currentSort) {
        InvestmentPoolBo targetPool = buildPool(2L, 1L, "目标池");
        targetPool.setPoolType("credit_bond");
        targetPool.setInnerSort(targetSort);
        InvestmentPoolBo currentPool = buildPool(3L, 1L, "当前池");
        currentPool.setPoolType("credit_bond");
        currentPool.setInnerSort(currentSort);

        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(targetPool.getId(), targetPool);
        poolMap.put(currentPool.getId(), currentPool);
        AdjustSharedData shared = new AdjustSharedData();
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(Collections.singleton(currentPool.getId()));

        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setTargetPoolId(targetPool.getId());
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setCanAdjust(true);

        List<AdjustCheckDto.FlowOption> options = ReflectionTestUtils.invokeMethod(
                service, "resolveAdjustFlowOptionsForItem", new AdjustCheckReq(), shared, item);
        return options.get(0);
    }

    /** 构造手工调入流程候选项测试行。 */
    private AdjustCheckDto.CheckResultItem buildManualInboundItem(Long targetPoolId) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setTargetPoolId(targetPoolId);
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setCanAdjust(true);
        return item;
    }

    /** 构造特殊审批命中共享数据。 */
    private AdjustSharedData buildSpecialInboundShared(Long targetPoolId, Long currentPoolId,
                                                       String targetPoolName, String currentPoolName) {
        InvestmentPoolBo targetPool = buildPool(targetPoolId, null, targetPoolName);
        InvestmentPoolBo currentPool = buildPool(currentPoolId, null, currentPoolName);
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

    /** 构造 CRMW 特殊审批命中共享数据。 */
    private com.znty.rrs.entity.crmwpooladjust.AdjustSharedData buildCrmwSpecialInboundShared(
            Long targetPoolId, Long currentPoolId, String targetPoolName, String currentPoolName) {
        InvestmentPoolBo targetPool = buildPool(targetPoolId, null, targetPoolName);
        InvestmentPoolBo currentPool = buildPool(currentPoolId, null, currentPoolName);
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        poolMap.put(targetPoolId, targetPool);
        poolMap.put(currentPoolId, currentPool);

        Map<String, List<Long>> targetRelations = new HashMap<>();
        targetRelations.put(RelationType.IN_MUTEX.getCode(), Collections.singletonList(currentPoolId));
        Map<Long, Map<String, List<Long>>> relationMap = new HashMap<>();
        relationMap.put(targetPoolId, targetRelations);

        com.znty.rrs.entity.crmwpooladjust.AdjustSharedData shared =
                new com.znty.rrs.entity.crmwpooladjust.AdjustSharedData();
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(Collections.singleton(currentPoolId));
        shared.setPoolRelationMap(relationMap);
        return shared;
    }

    /** 构建流程定义测试数据。 */
    private FlowDefinitionBo buildFlowDefinition(Long id, String flowKey, String name) {
        FlowDefinitionBo flow = new FlowDefinitionBo();
        flow.setId(id);
        flow.setFlowKey(flowKey);
        flow.setName(name);
        return flow;
    }

    private Object buildSnapshot(List<FlowNodeBo> nodes, List<FlowEdgeBo> edges,
                                 List<NodeApprovalConfigBo> configs,
                                 Map<Long, List<NodeApprovalHandlerBo>> handlerMap) throws Exception {
        Map<Long, FlowNodeBo> nodeMap = new LinkedHashMap<>();
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        for (NodeApprovalConfigBo config : configs) {
            configMap.put(config.getNodeId(), config);
        }
        Class<?> snapshotClass = Class.forName("com.znty.rrs.service.SecurityPoolAdjustService$FlowSnapshot");
        Constructor<?> constructor = snapshotClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, nodeMap, edges, configMap, handlerMap);
    }

    /** 构建调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData() throws Exception {
        return buildSubmitSharedData(new HashMap<Long, Object>());
    }

    /** 构建包含流程快照的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Map<Long, Object> snapshotMap) throws Exception {
        Object batchNoContext = buildBatchNoContext();
        return buildSubmitSharedData(snapshotMap, batchNoContext);
    }

    /** 构建指定批次号上下文的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Object batchNoContext) throws Exception {
        return buildSubmitSharedData(new HashMap<Long, Object>(), batchNoContext);
    }

    /** 构建批次号上下文测试数据。 */
    private Object buildBatchNoContext() throws Exception {
        Class<?> batchNoClass = Class.forName("com.znty.rrs.service.SecurityPoolAdjustService$BatchNoContext");
        Constructor<?> batchNoConstructor = batchNoClass.getDeclaredConstructors()[0];
        batchNoConstructor.setAccessible(true);
        return batchNoConstructor.newInstance();
    }

    /** 构建包含流程快照和批次号上下文的调库提交共享数据测试数据。 */
    private Object buildSubmitSharedData(Map<Long, Object> snapshotMap, Object batchNoContext) throws Exception {
        Class<?> sharedClass = Class.forName("com.znty.rrs.service.SecurityPoolAdjustService$SubmitSharedData");
        Constructor<?> constructor = sharedClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                new SecurityInfoBo(),
                new HashMap<Long, com.znty.rrs.entity.bo.InvestmentPoolBo>(),
                Collections.<Long>emptySet(),
                new HashMap<Long, Map<String, List<Long>>>(),
                false,
                false,
                false,
                snapshotMap,
                batchNoContext);
    }

    /** 构建直通调入提交请求。 */
    private SecurityPoolAdjustSubmitReq buildDirectInboundSubmitReq(String securityCode, String groupKey) {
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode(securityCode);
        req.setSecurityShortName("test");
        req.setSecurityType("bond");
        req.setAdjustType("manual");
        req.setAdjusterId("1");
        req.setAdjusterName("admin");

        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setAdjustMode("调入");
        item.setTargetPoolId(10L);
        item.setTargetPoolName("pool");
        item.setPoolType("special_account");
        item.setItemTag("manual");
        item.setAdjustGroupKey(groupKey);
        req.setItems(Collections.singletonList(item));
        return req;
    }

    /** 构建流程节点测试数据。 */
    private FlowNodeBo buildNode(Long id, String nodeId, String nodeType, Integer sortOrder) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setLabel(nodeId);
        node.setSortOrder(sortOrder);
        return node;
    }

    /** 构建流程连线测试数据。 */
    private FlowEdgeBo buildEdge(Long fromNodeId, Long toNodeId) {
        return buildEdge(fromNodeId, toNodeId, null);
    }

    /** 构建流程连线测试数据。 */
    private FlowEdgeBo buildEdge(Long fromNodeId, Long toNodeId, String routeAction) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        edge.setRouteAction(routeAction);
        return edge;
    }

    /** 构建投资池测试数据。 */
    private InvestmentPoolBo buildPool(Long id, Long parentId, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setParentId(parentId);
        pool.setPoolName(poolName);
        return pool;
    }

    /** 构建投资池权限测试数据。 */
    private PoolPermissionBo buildPermission(Long poolId, String handlerType, Long handlerId) {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(poolId);
        permission.setPermissionType("adjustable");
        permission.setHandlerType(handlerType);
        permission.setHandlerId(handlerId);
        return permission;
    }

    /** 构建审批配置测试数据。 */
    private NodeApprovalConfigBo buildConfig(Long id, Long nodeId, String approvalStrategy) {
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(id);
        config.setNodeId(nodeId);
        config.setApprovalStrategy(approvalStrategy);
        return config;
    }

    /** 构建审批处理人映射测试数据。 */
    private Map<Long, List<NodeApprovalHandlerBo>> buildHandlerMap(Long configId, int count) {
        Map<Long, List<NodeApprovalHandlerBo>> map = new HashMap<>();
        List<NodeApprovalHandlerBo> handlers = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            NodeApprovalHandlerBo handler = new NodeApprovalHandlerBo();
            handler.setApprovalConfigId(configId);
            handler.setHandlerType("user");
            handler.setHandlerId(i);
            handler.setHandlerName("user" + i);
            handlers.add(handler);
        }
        map.put(configId, handlers);
        return map;
    }

    // ===== 白名单流程 5 条件判断测试（isWhitelistFlowMatched）=====

    /** 验证白名单条件1：剩余期限超过3年时不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldFailWhenRemainDaysOver3Years() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("bond")).thenReturn("bond");
        AdjustSharedData shared = buildWhitelistShared("20990101", 0, 0, "公募", "bond", 0);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("超过 3 年")));
    }

    /** 验证白名单条件2：永续债时不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldFailWhenPerpetual() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("bond")).thenReturn("bond");
        AdjustSharedData shared = buildWhitelistShared("20270101", 1, 0, "公募", "bond", 0);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("永续债")));
    }

    /** 验证白名单条件2：ABS债时不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldFailWhenAbs() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("bond")).thenReturn("bond");
        AdjustSharedData shared = buildWhitelistShared("20270101", 0, 1, "公募", "bond", 0);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("ABS")));
    }

    /** 验证白名单条件3：非债券类时不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldFailWhenNotBondCategory() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("stock")).thenReturn("stock");
        AdjustSharedData shared = buildWhitelistShared("20270101", 0, 0, "公募", "stock", 0);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("不属于债券类")));
    }

    /** 验证白名单条件5：担保债时不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldFailWhenGuaranteed() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("bond")).thenReturn("bond");
        AdjustSharedData shared = buildWhitelistShared("20270101", 0, 0, "公募", "bond", 1);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("担保债")));
    }

    /** 验证白名单其它条件满足时仅条件4（白名单池未配置）不命中。 */
    @Test
    public void isWhitelistFlowMatchedShouldOnlyFailOnWhitelistPoolWhenOthersMatch() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        when(mapper.queryCategoryTypeBySecurityType("bond")).thenReturn("bond");
        AdjustSharedData shared = buildWhitelistShared("20270101", 0, 0, "公募", "bond", 0);
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        Boolean result = ReflectionTestUtils.invokeMethod(service, "isWhitelistFlowMatched",
                new AdjustCheckReq(), shared, matchReasons, unmatchReasons);
        // WHITELIST_POOL_IDS 常量空集，条件4恒 false，整体不命中
        assertThat(result).isFalse();
        assertTrue(unmatchReasons.stream().anyMatch(s -> s.contains("白名单池未配置")));
        assertTrue(matchReasons.stream().anyMatch(s -> s.contains("非永续债")));
    }

    /** 验证简易流程第⑤条件：三标志常量 false 时走"未下调"分支。 */
    @Test
    public void isSimpleInboundFlowMatchedShouldPassRatingCheckWhenFlagsFalse() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        InvestmentPoolBo targetPool = buildPool(2L, 1L, "一级库");
        targetPool.setPoolType("credit_bond");
        targetPool.setInnerSort(1);
        AdjustSharedData shared = new AdjustSharedData();
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDateNext("20270101");
        shared.setSecurityInfo(sec);
        when(mapper.queryIssuerTargetPoolMaxRemainDays(any(String.class), any(Long.class))).thenReturn(null);
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("110010123");
        List<String> matchReasons = new ArrayList<>();
        List<String> unmatchReasons = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(service, "isSimpleInboundFlowMatched",
                req, shared, targetPool, matchReasons, unmatchReasons);
        // 三标志常量 false，第⑤条件走"未下调"分支
        assertTrue(matchReasons.stream().anyMatch(s -> s.contains("主体评级和展望评级未下调")));
    }

    /** 验证证券信息合并不会清空页面不可编辑字段。 */
    @Test
    public void mergeSecurityInfoShouldKeepNonEditableFields() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityInfoBo current = new SecurityInfoBo();
        current.setShortName("原简称");
        current.setDateRepurchaseExists("20280101");
        current.setGuarantFlag(1);
        current.setGuarantType("连带责任担保");
        current.setAbsFlag(1);
        SecurityInfoBo changed = new SecurityInfoBo();
        changed.setShortName("新简称");

        ReflectionTestUtils.invokeMethod(service, "mergeSecurityInfo", current, changed);

        assertThat(current.getShortName()).isEqualTo("新简称");
        assertThat(current.getDateRepurchaseExists()).isEqualTo("20280101");
        assertThat(current.getGuarantFlag()).isEqualTo(1);
        assertThat(current.getGuarantType()).isEqualTo("连带责任担保");
        assertThat(current.getAbsFlag()).isEqualTo(1);
    }

    /** 验证最终落池时调入写入结果异常会阻断事务。 */
    @Test
    public void applyPoolStatusChangesShouldFailWhenInboundInsertCountUnexpected() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(1L);
        log.setSecurityCode("S001");
        log.setAdjustMode("调入");
        log.setTargetPoolName("一级库");
        when(mapper.addPoolStatus(log)).thenReturn(0);

        assertThatThrownBy(() -> service.applyPoolStatusChanges(Collections.singletonList(log)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入池状态写入异常");
    }

    // ===== 节点语义 approval_strategy 判断测试（isInitiatorStep）=====

    /** 验证发起人节点 approval_strategy=initiator 时 isInitiatorStep 返回 true。 */
    @Test
    public void isInitiatorStepShouldReturnTrueWhenInitiatorStrategy() throws Exception {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        FlowNodeBo node = new FlowNodeBo();
        node.setId(1L);
        node.setNodeType("approval");
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setNodeId(node.getId());
        config.setApprovalStrategy("initiator");
        Object snapshot = buildSnapshot(Collections.singletonList(node),
                Collections.singletonList(buildEdge(node.getId(), 2L, "submit")),
                Collections.singletonList(config),
                Collections.<Long, List<NodeApprovalHandlerBo>>emptyMap());
        Boolean result = ReflectionTestUtils.invokeMethod(
                service, "isInitiatorStep", snapshot, node, config, null, null);
        assertThat(result).isTrue();
    }

    /** 验证 approval_strategy=preempt 时 isInitiatorStep 返回 false。 */
    @Test
    public void isInitiatorStepShouldReturnFalseWhenNotInitiator() throws Exception {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        FlowNodeBo node = new FlowNodeBo();
        node.setId(1L);
        node.setNodeType("approval");
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setNodeId(node.getId());
        config.setApprovalStrategy("preempt");
        Object snapshot = buildSnapshot(Collections.singletonList(node),
                Collections.singletonList(buildEdge(node.getId(), 2L, "submit")),
                Collections.singletonList(config),
                Collections.<Long, List<NodeApprovalHandlerBo>>emptyMap());
        Boolean result = ReflectionTestUtils.invokeMethod(
                service, "isInitiatorStep", snapshot, node, config, null, null);
        assertThat(result).isFalse();
    }

    /** 构建白名单测试共享数据。 */
    private AdjustSharedData buildWhitelistShared(String dateNext, int yxFlag, int absFlag,
                                                  String issueType, String securityType, int guarantFlag) {
        AdjustSharedData shared = new AdjustSharedData();
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDateNext(dateNext);
        sec.setYxFlag(yxFlag);
        sec.setAbsFlag(absFlag);
        sec.setIssueType(issueType);
        sec.setSecurityType(securityType);
        sec.setGuarantFlag(guarantFlag);
        shared.setSecurityInfo(sec);
        return shared;
    }
}

package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.Row;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.ScheduledSecurity;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证批次数据库取数和页面、任务共同遵循的准入约束 */
public class SecurityPoolBatchCheckTest {

    /** 整批请求的必填项先在批量服务检查，再读取校验数据 */
    @Test
    public void rejectsIncompleteRequestsBeforeLoadingBatchData() {
        BatchSecurityCheckService service = new BatchSecurityCheckService();
        AdjustCheckReq missingCode = request(null);
        AdjustCheckReq missingItems = new AdjustCheckReq();
        missingItems.setSecurityCode("B2");

        assertThatThrownBy(() -> service.checkSecurityBatch(Arrays.asList(request("B1"), missingCode)))
                .isInstanceOf(BizException.class).hasMessage("证券代码不能为空");
        assertThatThrownBy(() -> service.checkSecurityBatch(Arrays.asList(request("B1"), missingItems)))
                .isInstanceOf(BizException.class).hasMessage("调库项不能为空");
    }

    /** 1000 只券同池检查，每类证券查询只分成两批 */
    @Test
    public void loadsOneThousandBondsInTwoChunks() {
        Fixture fixture = new Fixture();
        List<AdjustCheckReq> requests = IntStream.range(0, 1000)
                .mapToObj(i -> request("B" + i)).collect(Collectors.toList());

        List<AdjustCheckDto> results = fixture.service.checkSecurityBatch(requests);

        assertThat(results).hasSize(1000).allSatisfy(result ->
                assertThat(result.getItems()).singleElement().satisfies(item -> assertThat(item.isCanAdjust()).isTrue()));
        verify(fixture.pools).queryPoolList();
        verify(fixture.batch).queryPoolRelations();
        verify(fixture.batch).queryPoolCounts(any());
        verify(fixture.batch, times(2)).querySecurities(anyList());
        verify(fixture.batch, times(3)).queryActiveStates(anyList());
        verify(fixture.batch, times(2)).queryPendingPools(anyList());
        verify(fixture.batch, times(2)).queryPendingLabels(anyList());
        verify(fixture.batch, times(2)).queryRelatedCodes(anyList(), anyList());
        verify(fixture.market, times(2)).queryMarketMappingList(anyList());
        verifyNoInteractions(fixture.adjust);
    }

    /** 目标池配置了批量流程时，页面调入和调出仍沿用单笔候选及推荐 */
    @Test
    public void pageKeepsSingleFlowRecommendationsForBothDirections() {
        Fixture fixture = new Fixture();
        fixture.target.setInFlowId(11L);
        fixture.target.setOutFlowId(12L);
        fixture.target.setBatchInFlowId(21L);
        fixture.target.setBatchOutFlowId(22L);
        AdjustCheckReq inbound = request("B1");
        AdjustCheckReq outbound = request("B2");
        outbound.getItems().get(0).setAdjustMode(AdjustMode.OUT.getCode());
        Row existing = new Row();
        existing.setSecurityCode("B2");
        existing.setPoolId(fixture.target.getId());
        when(fixture.batch.queryActiveStates(anyList())).thenReturn(Collections.singletonList(existing));

        List<AdjustCheckDto> results = fixture.service.checkSecurityBatch(Arrays.asList(inbound, outbound));

        assertThat(results.get(0).getItems()).singleElement().satisfies(item -> {
            assertThat(item.isCanAdjust()).isTrue();
            assertThat(item.getFlowOptions()).singleElement().satisfies(option -> {
                assertThat(option.getFlowType()).isEqualTo(FlowType.NORMAL_INBOUND.getCode());
                assertThat(option.getFlowId()).isEqualTo(11L);
                assertThat(option.isRecommended()).isTrue();
            });
        });
        assertThat(results.get(1).getItems()).singleElement().satisfies(item -> {
            assertThat(item.isCanAdjust()).isTrue();
            assertThat(item.getFlowOptions()).singleElement().satisfies(option -> {
                assertThat(option.getFlowType()).isEqualTo(FlowType.NORMAL_OUTBOUND.getCode());
                assertThat(option.getFlowId()).isEqualTo(12L);
                assertThat(option.isRecommended()).isTrue();
            });
        });
    }

    /** 千行 Excel 复用整批数据，原业务失败仍作为调整提示 */
    @Test
    public void excelUsesTwoChunksAndKeepsBusinessWarnings() {
        Fixture fixture = new Fixture();
        List<AdjustCheckReq> requests = IntStream.range(0, 1000)
                .mapToObj(i -> request("B" + i)).collect(Collectors.toList());
        InvestmentPoolBo forbidden = new InvestmentPoolBo();
        forbidden.setId(15L);
        forbidden.setPoolType("forbidden");
        forbidden.setIsDeleted(0);
        when(fixture.pools.queryPoolList()).thenReturn(Arrays.asList(fixture.target, forbidden));
        Row blocked = new Row();
        blocked.setSecurityCode("B0");
        blocked.setPoolId(15L);
        List<String> firstChunk = requests.subList(0, 500).stream()
                .map(AdjustCheckReq::getSecurityCode).collect(Collectors.toList());
        when(fixture.batch.queryActiveStates(firstChunk)).thenReturn(Collections.singletonList(blocked));

        BatchSecurityCheckData data = fixture.service.loadExcelCheckData(requests, false);
        List<AdjustCheckDto> results = requests.stream().map(req -> fixture.service.checkExcelItem(req, data))
                .collect(Collectors.toList());

        assertThat(results).hasSize(1000);
        assertThat(results.get(0).getItems()).singleElement().satisfies(item -> {
            assertThat(item.isCanAdjust()).isTrue();
            assertThat(item.getFailReasons()).contains("证券当前在禁止池中");
        });
        assertThat(results.get(999).getItems()).singleElement().satisfies(item -> {
            assertThat(item.isCanAdjust()).isTrue();
            assertThat(item.getFailReasons()).isEmpty();
        });
        verify(fixture.pools).queryPoolList();
        verify(fixture.batch, times(2)).querySecurities(anyList());
        verify(fixture.batch, times(2)).queryRatingCompanies(anyList());
        verify(fixture.batch, times(2)).queryPendingPools(anyList());
        verifyNoInteractions(fixture.adjust);
    }

    /** Excel 按主档实际代码匹配大小写不同的输入，并复用已查主档 */
    @Test
    public void excelResolvesMasterCodeBeforeLoadingCandidatesAndRelatedSecurities() {
        Fixture fixture = new Fixture();
        SecurityInfoBo master = new SecurityInfoBo();
        master.setWindCode("CRMW001.Ib");
        master.setSecurityType("mtn");
        master.setIssuerCode("ISSUER");
        List<String> inputs = Arrays.asList("crmw001.ib", "CRMW001.IB");
        List<String> masterCodes = Collections.singletonList(master.getWindCode());
        when(fixture.batch.querySecurities(inputs)).thenReturn(Collections.singletonList(master));
        RelatedRatingCompanyDto candidate = new RelatedRatingCompanyDto();
        candidate.setSecurityCode(master.getWindCode());
        candidate.setCompanyCode("GUARANTOR");
        when(fixture.batch.queryRatingCompanies(masterCodes)).thenReturn(Collections.singletonList(candidate));

        BatchSecurityCheckData preview = fixture.service.loadExcelRatingCandidates(inputs, Collections.emptyList());
        assertThat(preview.resolveExcelSecurityCode(inputs.get(0))).isEqualTo(master.getWindCode());
        assertThat(preview.ratingCompanies.get(master.getWindCode())).containsExactly(candidate);

        List<AdjustCheckReq> requests = Arrays.asList(request(inputs.get(0)), request(inputs.get(1)));
        BatchSecurityCheckData data = fixture.service.loadExcelCheckData(requests, false);
        for (AdjustCheckReq request : requests) {
            assertThat(request.getSecurityCode()).isEqualTo(master.getWindCode());
            assertThat(fixture.service.checkExcelItem(request, data).getItems()).singleElement().satisfies(item -> {
                assertThat(item.isCanAdjust()).isTrue();
                assertThat(item.getSecurityCode()).isEqualTo(master.getWindCode());
            });
        }
        verify(fixture.batch, times(2)).querySecurities(anyList());
        verify(fixture.batch, times(2)).queryRatingCompanies(masterCodes);
        verify(fixture.batch).queryRelatedCodes(eq(masterCodes), anyList());
        verify(fixture.market).queryMarketMappingList(masterCodes);
        verifyNoInteractions(fixture.adjust);
    }

    /** 首屏沿用本次校验的候选，仅补查池名或权限校验未通过的行及其展示信息 */
    @Test
    public void excelFirstPageOnlyCompletesMissingRatingData() {
        Fixture fixture = new Fixture();
        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setWindCode("ABS.IB");
        abs.setSecurityType("abs");
        SecurityInfoBo skipped = new SecurityInfoBo();
        skipped.setWindCode("SKIPPED.IB");
        skipped.setSecurityType("mtn");
        List<String> checkedCodes = Arrays.asList("abs.ib", "NOT_FOUND");
        List<String> checkedCandidates = Arrays.asList("ABS.IB", "NOT_FOUND");
        List<String> skippedInput = Collections.singletonList("skipped.ib");
        List<String> skippedCodes = Collections.singletonList("SKIPPED.IB");
        when(fixture.batch.querySecurities(checkedCodes)).thenReturn(Collections.singletonList(abs));
        when(fixture.batch.querySecurities(skippedInput)).thenReturn(Collections.singletonList(skipped));
        RelatedRatingCompanyDto related = new RelatedRatingCompanyDto();
        related.setSecurityCode("ABS.IB");
        related.setCompanyCode("RELATED");
        when(fixture.batch.queryRatingCompanies(checkedCandidates)).thenReturn(Collections.singletonList(related));
        SelfSelectedRightsHolderDto selected = new SelfSelectedRightsHolderDto();
        selected.setCompanyCode("SELECTED");
        selected.setInnerRating("1");
        when(fixture.batch.querySelfSelectedCompanies(Arrays.asList("SELECTED", "NOT_ACTIVE")))
                .thenReturn(Collections.singletonList(selected));
        AdjustCheckReq first = request("abs.ib");
        first.setSelfSelectedRightsHolderCode("SELECTED");
        AdjustCheckReq second = request("abs.ib");
        second.setSelfSelectedRightsHolderCode("NOT_ACTIVE");
        BatchSecurityCheckData data = fixture.service.loadExcelCheckData(Arrays.asList(first, second, request("NOT_FOUND")), false);

        List<String> pageCodes = Arrays.asList("ABS.IB", "NOT_FOUND", "skipped.ib");
        List<String> pageSelf = Arrays.asList("selected", "NOT_ACTIVE", "DISPLAY_ONLY");
        fixture.service.completeExcelRatingCandidates(data, pageCodes, pageSelf);
        fixture.service.completeExcelRatingCandidates(data, pageCodes, pageSelf);

        assertThat(data.resolveExcelSecurityCode("skipped.ib")).isEqualTo("SKIPPED.IB");
        assertThat(data.ratingCompanies.get("ABS.IB")).containsExactly(related);
        assertThat(data.selfSelectedCompanies.get("selected")).isSameAs(selected);
        verify(fixture.batch).querySecurities(checkedCodes);
        verify(fixture.batch).querySecurities(skippedInput);
        verify(fixture.batch, times(2)).querySecurities(anyList());
        verify(fixture.batch).queryRatingCompanies(checkedCandidates);
        verify(fixture.batch).queryRatingCompanies(skippedCodes);
        verify(fixture.batch, times(2)).queryRatingCompanies(anyList());
        verify(fixture.batch).querySelfSelectedCompanies(Arrays.asList("SELECTED", "NOT_ACTIVE"));
        verify(fixture.batch).querySelfSelectedCompanies(Collections.singletonList("DISPLAY_ONLY"));
        verify(fixture.batch, times(2)).querySelfSelectedCompanies(anyList());
    }

    /** ABS 主体选择、在途冲突和同批冲突都使用预加载数据 */
    @Test
    public void checksAbsIdentitiesWithoutReadingSingleSecurityMapper() {
        Fixture fixture = new Fixture();
        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setWindCode("ABS1");
        abs.setSecurityType("abs");
        when(fixture.batch.querySecurities(anyList())).thenReturn(Collections.singletonList(abs));
        Row category = new Row();
        category.setSecurityType("abs");
        category.setCategoryType("bond");
        when(fixture.batch.queryCategories()).thenReturn(Collections.singletonList(category));

        RelatedRatingCompanyDto first = new RelatedRatingCompanyDto();
        first.setSecurityCode("ABS1");
        first.setCompanyCode("A");
        first.setCompanyName("A");
        when(fixture.batch.queryRatingCompanies(anyList())).thenReturn(Collections.singletonList(first));
        SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
        self.setCompanyCode("B");
        self.setCompanyName("B");
        when(fixture.batch.querySelfSelectedCompanies(Collections.singletonList("B")))
                .thenReturn(Collections.singletonList(self));
        Row occupied = new Row();
        occupied.setSecurityCode("ABS1");
        occupied.setCompanyCode("A");
        when(fixture.batch.queryAbsIdentities(Collections.singletonList("ABS1")))
                .thenReturn(Collections.singletonList(occupied));

        AdjustCheckReq ordinary = request("ABS1");
        AdjustCheckReq different = request("ABS1");
        different.setSelfSelectedRightsHolderCode("B");
        List<AdjustCheckDto> results = fixture.service.checkSecurityBatch(Arrays.asList(ordinary, different));

        assertThat(results).allSatisfy(result -> assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("在同批申请中选择了不同主体"));
        }));
        assertThat(results.get(1).getItems().get(0).getFailReasons())
                .anyMatch(reason -> reason.contains("已绑定或存在在途主体[A]"));
        verify(fixture.batch).queryAbsIdentities(Collections.singletonList("ABS1"));
        verify(fixture.batch).querySelfSelectedCompanies(Collections.singletonList("B"));
        verifyNoInteractions(fixture.adjust);
    }

    /** 同一禁止库原因在页面和任务都拦截，任务组间累计实际计划容量 */
    @Test
    public void appliesSharedRulesAndReservesOnlyApprovedGroups() {
        Fixture fixture = new Fixture();
        fixture.target.setMaxCapacity(1L);
        InvestmentPoolBo forbidden = new InvestmentPoolBo();
        forbidden.setId(15L);
        forbidden.setPoolType("forbidden");
        forbidden.setIsDeleted(0);
        when(fixture.pools.queryPoolList()).thenReturn(Arrays.asList(fixture.target, forbidden));
        Row blocked = new Row();
        blocked.setSecurityCode("B1");
        blocked.setPoolId(15L);
        when(fixture.batch.queryActiveStates(Arrays.asList("B1", "B2", "B3", "ISSUER")))
                .thenReturn(Collections.singletonList(blocked));
        List<AdjustCheckReq> requests = Arrays.asList(request("B1"), request("B2"), request("B3"));

        List<AdjustCheckDto> page = fixture.service.checkSecurityBatch(requests);
        List<AdjustCheckDto> task = fixture.service.checkCompanyFollowBatch(requests, "20261119", Collections.emptySet());

        assertThat(page.get(0).getItems().get(0).getFailReasons()).contains("证券当前在禁止池中");
        assertThat(task.get(0).getItems().get(0).getFailReasons())
                .isEqualTo(page.get(0).getItems().get(0).getFailReasons());
        assertThat(task.get(1).getItems().get(0).isCanAdjust()).isTrue();
        assertThat(task.get(2).getItems().get(0).getFailReasons()).contains("目标投资池已达持仓上限（1）");
        verify(fixture.pools, never()).lockPoolByIdsList(anyList());
        verify(fixture.batch).queryScheduledSecurities(anyList());
    }

    /** 默认权益人按每 500 个代码重新查库，保留每只证券的首个候选 */
    @Test
    public void defaultRightsHoldersShouldUseCurrentDatabaseCandidatesInChunks() {
        Fixture fixture = new Fixture();
        List<String> codes = IntStream.range(0, 1000).mapToObj(i -> "ABS" + i + ".IB").collect(Collectors.toList());
        when(fixture.batch.queryRatingCompanies(anyList())).thenAnswer(call -> {
            List<String> chunk = call.getArgument(0);
            return chunk.stream().flatMap(code -> Arrays.asList("FIRST", "SECOND").stream().map(company -> {
                RelatedRatingCompanyDto row = new RelatedRatingCompanyDto();
                row.setSecurityCode(code);
                row.setCompanyCode(company);
                return row;
            })).collect(Collectors.toList());
        });

        Map<String, String> defaults = fixture.service.loadDefaultRightsHolders(new LinkedHashSet<>(codes));

        assertThat(defaults).hasSize(1000);
        assertThat(defaults.get("abs999.ib")).isEqualTo("FIRST");
        verify(fixture.batch).queryRatingCompanies(codes.subList(0, 500));
        verify(fixture.batch).queryRatingCompanies(codes.subList(500, 1000));
    }

    /** 有主档但是到期或转正资格不通过的债券仍由任务按原原因跳过 */
    @Test
    public void scheduledUsesEligibilityFromTheSameMasterQuery() {
        Fixture fixture = new Fixture();
        ScheduledSecurity ineligible = new ScheduledSecurity();
        ineligible.setWindCode("B1");
        ineligible.setSecurityType("mtn");
        ineligible.setIssuerCode("ISSUER");
        ineligible.setEligibleForFollow(true);
        String futureMaturity = java.time.LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String cutoff = java.time.LocalDate.now().plusDays(2).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        ineligible.setMaturityDate(futureMaturity);
        when(fixture.batch.queryScheduledSecurities(Collections.singletonList("B1")))
                .thenReturn(Collections.singletonList(ineligible));

        AdjustCheckDto result = fixture.service.checkCompanyFollowBatch(Collections.singletonList(request("B1")), cutoff, Collections.emptySet())
                .get(0);

        assertThat(result.getItems().get(0).isCanAdjust()).isFalse();
        assertThat(result.getItems().get(0).getFailReasons()).contains("证券不符合定时补债范围或距到期天数不足");
        verify(fixture.batch, never()).querySecurities(anyList());
    }

    /** 配套联动也需要满足目标池容量和报告要求 */
    @Test
    public void scheduledDoesNotApplyIncompleteLinkedGroup() {
        Fixture fixture = new Fixture();
        InvestmentPoolBo linked = new InvestmentPoolBo();
        linked.setId(10L);
        linked.setPoolName("联动池");
        linked.setStatus("enabled");
        linked.setPoolType("observe");
        linked.setInReportRestriction("internal");
        when(fixture.pools.queryPoolList()).thenReturn(Arrays.asList(fixture.target, linked));
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(9L);
        relation.setRelationPoolId(10L);
        relation.setRelationType("in_linked");
        when(fixture.batch.queryPoolRelations()).thenReturn(Collections.singletonList(relation));

        AdjustCheckDto result = fixture.service.checkCompanyFollowBatch(Collections.singletonList(request("B1")), "20261118", Collections.emptySet())
                .get(0);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(1).getFailReasons()).contains("目标池要求研究报告且本次没有可用的报告豁免");
        verify(fixture.pools, never()).lockPoolByIdsList(anyList());
    }

    /** 构建只包含一个目标池的单券请求 */
    private AdjustCheckReq request(String code) {
        AdjustCheckReq request = new AdjustCheckReq();
        request.setSecurityCode(code);
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(9L);
        item.setAdjustMode(AdjustMode.IN.getCode());
        request.setItems(Collections.singletonList(item));
        return request;
    }

    /** 使用实际规则服务和可计数的 Mapper 模拟数据 */
    private static final class Fixture {
        private final BatchSecurityCheckService service = new BatchSecurityCheckService();
        private final BatchSecurityCheckMapper batch = mock(BatchSecurityCheckMapper.class);
        private final SecurityPoolAdjustMapper adjust = mock(SecurityPoolAdjustMapper.class);
        private final InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
        private final SecurityMarketMapper market = mock(SecurityMarketMapper.class);
        private final InvestmentPoolBo target = new InvestmentPoolBo();

        private Fixture() {
            ReflectionTestUtils.setField(service, "batchSecurityCheckMapper", batch);
            AutoAdjustTestSupport.bindExcelExclusion(service);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(service, "securityMarketMapper", market);
            AbsIdentityService identity = new AbsIdentityService();
            ReflectionTestUtils.setField(service, "absIdentityService", identity);
            SecurityPoolCheckRules rules = new SecurityPoolCheckRules(adjust, null, null, null, identity, null,
                    mock(RatingDowngradeChecker.class), false);
            ReflectionTestUtils.setField(service, "securityPoolCheckRules", rules);
            target.setId(9L);
            target.setStatus("enabled");
            target.setPoolType("observe");
            target.setMarketCodes("[\"NIB\"]");
            target.setVarietyCodes("[\"bond\"]");
            when(pools.queryPoolList()).thenReturn(Collections.singletonList(target));
            Row category = new Row();
            category.setSecurityType("mtn");
            category.setCategoryType("bond");
            when(batch.queryCategories()).thenReturn(Collections.singletonList(category));
            when(batch.querySecurities(anyList())).thenAnswer(call -> {
                List<String> codes = call.getArgument(0);
                return codes.stream().map(code -> {
                    SecurityInfoBo security = new SecurityInfoBo();
                    security.setWindCode(code);
                    security.setSecurityType("mtn");
                    security.setIssuerCode("ISSUER");
                    return security;
                }).collect(Collectors.toList());
            });
            when(market.queryMarketMappingList(anyList())).thenAnswer(call -> {
                List<String> codes = call.getArgument(0);
                return codes.stream().map(code -> {
                    SecurityMarketMappingDto mapping = new SecurityMarketMappingDto();
                    mapping.setWindCode(code);
                    mapping.setRawMarketCode("NIB");
                    return mapping;
                }).collect(Collectors.toList());
            });
            when(batch.queryScheduledSecurities(anyList())).thenAnswer(call -> {
                List<String> codes = call.getArgument(0);
                return codes.stream().map(code -> {
                    ScheduledSecurity security = new ScheduledSecurity();
                    security.setWindCode(code);
                    security.setSecurityType("mtn");
                    security.setIssuerCode("ISSUER");
                    security.setEligibleForFollow(true);
                    return security;
                }).collect(Collectors.toList());
            });
        }
    }
}

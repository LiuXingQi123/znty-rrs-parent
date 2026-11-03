package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialDto;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialSaveReq;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.ForbiddenAbsPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.exception.BizException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证客户端及本次已加载数据直接复用，减少主档、候选和保存后回查 */
public class LoadedDataReuseTest {

    /** 空权益人查询不读取财报或旧主档，保存也不能借用旧主体 */
    @Test
    public void emptySelectionShouldKeepFinancialDataEmptyWithoutQueries() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        RatingCompanySelectionReq empty = new RatingCompanySelectionReq();
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode("ABS1");
        req.setIssuerCode("OLD");
        req.setSelectedRatingCompany(empty);
        req.setReportDate(20251231L);
        assertThat(service.queryIssuerFinancialList(req)).isEmpty();
        IssuerFinancialDto report = service.queryIssuerFinancialByReportDate(req);
        assertThat(report.getReportDate()).isEqualTo(20251231L);
        assertThat(report.getTotAssets()).isNull();
        IssuerFinancialSaveReq save = new IssuerFinancialSaveReq();
        save.setSecurityCode("ABS1");
        save.setIssuerCode("OLD");
        save.setSelectedRatingCompany(empty);
        save.setRecords(Collections.singletonList(report));
        assertThatThrownBy(() -> service.saveIssuerFinancialList(save))
                .isInstanceOf(BizException.class).hasMessageContaining("请选择权益人或自选权益人");
        verifyNoInteractions(mapper);
    }

    /** 清空仅影响本次预览对象，下一步才提示必选主体，不额外查候选或名称 */
    @Test
    public void emptySelectionShouldBeAllowedInPreviewAndRequiredForNextStep() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        SecurityInfoBo security = new SecurityInfoBo();
        security.setSecurityType("abs");
        security.setIssuerCode("A");
        security.setIssuer("主体A");
        security.setInnerIssuerRating("1");
        RatingCompanySelectionReq empty = new RatingCompanySelectionReq();
        service.applySelectedRatingCompany(security, null, null, null, false, empty);
        assertThat(security.getIssuerCode()).isNull();
        assertThat(security.getIssuer()).isNull();
        assertThat(security.getInnerIssuerRating()).isNull();
        assertThatThrownBy(() -> service.applySelectedRatingCompany(security, null, null, null, true, empty))
                .isInstanceOf(BizException.class).hasMessage("请选择权益人或自选权益人");
        verifyNoInteractions(mapper);
    }

    /** 标的详情和凭证类型都已在页面取得，CRMW 校验只查询当前业务状态 */
    @Test
    public void crmwCheckShouldReuseSecurityAndCredentialType() {
        CrmwPoolAdjustService service = new CrmwPoolAdjustService();
        CrmwPoolAdjustMapper mapper = mock(CrmwPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", mock(SecurityPoolAdjustService.class));
        ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode("BOND1");
        security.setSecurityType("bond");
        AdjustCheckReq req = new AdjustCheckReq();
        req.setSecurityCode("BOND1");
        req.setSecurityInfo(security);
        req.setCrmwScode("C1");
        req.setCrmwStype("crmw");
        req.setItems(Collections.emptyList());
        ReflectionTestUtils.invokeMethod(service, "loadSharedData", req);
        verify(mapper, never()).querySecurityBoByCode("BOND1");
        verify(mapper, never()).querySecurityBoByCode("C1");
        verify(mapper).querySecurityCurrentPoolIdList("BOND1", "C1", "crmw");
    }

    /** 批量提交直接比较请求中的类型及主体，不逐项读取主档 */
    @Test
    public void batchIdentityComparisonShouldNotReadSecurityOrCompany() {
        AbsIdentityService service = new AbsIdentityService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        AbsIdentityMapper identities = mock(AbsIdentityMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "absIdentityMapper", identities);
        service.validateBatchSelection("BOND1", "bond", null, null, new HashMap<>());
        service.validateBatchSelection("ABS1", "abs", "A", "B", new HashMap<>());
        verifyNoInteractions(mapper, identities);
    }

    /** 页面主体的空评分也直接采用，不为校验名称或评级额外查询 */
    @Test
    public void selectedCompanyShouldFillFiveFieldsWithoutAnyLookup() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        AbsIdentityService identity = new AbsIdentityService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        AbsIdentityMapper identities = mock(AbsIdentityMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "absIdentityService", identity);
        ReflectionTestUtils.setField(identity, "absIdentityMapper", identities);
        SecurityInfoBo security = new SecurityInfoBo();
        security.setSecurityType("abs");
        RatingCompanySelectionReq selected = new RatingCompanySelectionReq();
        selected.setCompanyCode("B");
        selected.setCompanyName("主体B");
        selected.setRatingBondissuer("AA+");
        selected.setRatingBondissuerAgency("R2");
        selected.setRightsHolderName("主体A");
        selected.setSelfSelectedRightsHolderName("主体B");

        SecurityPoolAdjustService.SelectedRatingCompanyData result = service.applySelectedRatingCompany(
                security, null, "A", "B", true, selected);

        assertThat(security.getIssuerCode()).isEqualTo("B");
        assertThat(security.getIssuer()).isEqualTo("主体B");
        assertThat(security.getInnerIssuerRating()).isNull();
        assertThat(security.getRatingBondissuer()).isEqualTo("AA+");
        assertThat(security.getRatingBondissuerAgency()).isEqualTo("R2");
        assertThat(result.getRightsHolderName()).isEqualTo("主体A");
        assertThat(result.getSelfSelectedRightsHolderName()).isEqualTo("主体B");
        verifyNoInteractions(mapper, identities);
    }

    /** 已选主体的四期查询只读取财报，不查证券、关系或评级 */
    @Test
    public void financialQueryShouldUseSelectedCodeDirectly() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode("ABS1");
        req.setRightsHolderCode("A");
        req.setSelfSelectedRightsHolderCode("B");
        when(mapper.queryLatestFinancialDateByIssuer("B")).thenReturn(null);
        when(mapper.queryFinancialListByIssuer(eq("B"), anyList())).thenReturn(Collections.emptyList());

        assertThat(service.queryIssuerFinancialList(req)).hasSize(4);

        verify(mapper).queryLatestFinancialDateByIssuer("B");
        verify(mapper).queryFinancialListByIssuer(eq("B"), anyList());
        verify(mapper, never()).querySecurityBoByCode("ABS1");
        verify(mapper, never()).queryRelatedRatingCompanyList("ABS1");
        verify(mapper, never()).querySelfSelectedRightsHolderByCode("B");
    }

    /** 两期完整财报保存只写两次，不读取主档，也不在保存后重复读取财报 */
    @Test
    public void financialSaveShouldReturnSubmittedMetricsWithoutReadingThemAgain() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        IssuerFinancialDto first = new IssuerFinancialDto();
        first.setReportDate(20241231L);
        first.setTotAssets(BigDecimal.TEN);
        IssuerFinancialDto second = new IssuerFinancialDto();
        second.setReportDate(20250331L);
        second.setTotAssets(BigDecimal.ONE);
        IssuerFinancialSaveReq req = new IssuerFinancialSaveReq();
        req.setSecurityCode("ABS1");
        req.setIssuerCode("B");
        req.setRecords(Arrays.asList(first, second));

        List<IssuerFinancialDto> saved = service.saveIssuerFinancialList(req);

        assertThat(saved).containsExactly(first, second);
        verify(mapper).saveIssuerFinancial("B", first);
        verify(mapper).saveIssuerFinancial("B", second);
        verify(mapper, never()).querySecurityBoByCode("ABS1");
        verify(mapper, never()).queryFinancialByIssuerAndDate("B", 20241231L);
        verify(mapper, never()).queryFinancialByIssuerAndDate("B", 20250331L);
    }

    /** 身份恢复复用已经取得的主档和占用集合，状态仅查询一次 */
    @Test
    public void identityRestoreShouldNotReadMasterOrOccupancyTwice() {
        AbsIdentityService service = new AbsIdentityService();
        AbsIdentityMapper mapper = mock(AbsIdentityMapper.class);
        SecurityPoolAdjustMapper securities = mock(SecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "absIdentityMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securities);
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode("ABS1");
        security.setSecurityType("abs");
        security.setIssuerCode("A");
        security.setIssuer("主体A");
        when(mapper.queryOccupiedIdentityCodes("ABS1")).thenReturn(Arrays.asList("A", "A"));

        assertThat(service.queryCurrentIdentity(security).getIssuer()).isEqualTo("主体A");

        verify(mapper, times(1)).queryOccupiedIdentityCodes("ABS1");
        verifyNoInteractions(securities);
        verify(mapper, never()).queryPendingIdentityDetail("ABS1");
    }

    /** 三条链路构建快照均直接复制本次已有主档 */
    @Test
    public void allSnapshotBuildersShouldReuseLoadedSecurity() {
        Object[] services = { new SecurityPoolAdjustService(), new ForbiddenAbsPoolAdjustService(), new CrmwPoolAdjustService() };
        Object[] mappers = { mock(SecurityPoolAdjustMapper.class), mock(ForbiddenAbsPoolAdjustMapper.class), mock(CrmwPoolAdjustMapper.class) };
        String[] fields = { "securityPoolAdjustMapper", "forbiddenAbsPoolAdjustMapper", "crmwPoolAdjustMapper" };
        for (int index = 0; index < services.length; index++) {
            ReflectionTestUtils.setField(services[index], fields[index], mappers[index]);
            SecurityInfoBo loaded = new SecurityInfoBo();
            loaded.setWindCode("BOND1");
            loaded.setSecurityType("bond");
            loaded.setAbsFlag(0);
            SecurityInfoBo edited = new SecurityInfoBo();
            edited.setWindCode("BOND1");
            edited.setFullName("本次名称");
            SecurityInfoBo snapshot = ReflectionTestUtils.invokeMethod(services[index], "buildMergedSecurityInfo", "BOND1", edited, loaded);
            assertThat(snapshot.getFullName()).isEqualTo("本次名称");
            assertThat(snapshot.getSecurityType()).isEqualTo("bond");
            assertThat(loaded.getFullName()).isNull();
            verifyNoInteractions(mappers[index]);
        }
    }
}

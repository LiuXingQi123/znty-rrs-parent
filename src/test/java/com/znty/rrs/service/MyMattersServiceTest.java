package com.znty.rrs.service;

import com.znty.rrs.entity.mymatters.BusinessDomainDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BusinessPermissionMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 入口选项与事项查询解耦、业务分派和输入参数校验测试。 */
public class MyMattersServiceTest {
    /** 我的事宜统一查询入口 */
    private MyMattersService service;
    /** 页面入口选项查询 */
    private BusinessPermissionMapper entries;
    /** 债券事项查询 */
    private BondMyMattersService bond;
    /** 基金事项查询 */
    private FundMyMattersService fund;

    /** 初始化独立的入口和领域查询组件。 */
    @Before
    public void setUp() {
        service = new MyMattersService();
        entries = mock(BusinessPermissionMapper.class);
        bond = mock(BondMyMattersService.class);
        fund = mock(FundMyMattersService.class);
        ReflectionTestUtils.setField(service, "businessPermissionMapper", entries);
        ReflectionTestUtils.setField(service, "bondMyMattersService", bond);
        ReflectionTestUtils.setField(service, "fundMyMattersService", fund);
    }

    /** 只有进入页面查询选项时才读取当前用户入口。 */
    @Test public void shouldQueryBusinessOptionsOnlyOnEntry() {
        BusinessDomainDto bondDomain = new BusinessDomainDto();
        bondDomain.setBusinessDomain("bond");
        BusinessDomainDto fundDomain = new BusinessDomainDto();
        fundDomain.setBusinessDomain("fund");
        List<BusinessDomainDto> domains = Arrays.asList(bondDomain, fundDomain);
        when(entries.queryBusinessDomainList(2L)).thenReturn(domains);
        // 构造合法用户的页面入口请求
        MyMattersReq req = request("fund", "2");
        assertThat(service.queryBusinessDomainList(req)).isSameAs(domains);
        verify(entries).queryBusinessDomainList(2L);
        verifyNoInteractions(bond, fund);
    }

    /** 无可见入口也不阻止合法领域请求，事项和角标查询不重复读取入口。 */
    @Test public void shouldDispatchQueriesWithoutReadingBusinessOptions() {
        when(entries.queryBusinessDomainList(2L)).thenReturn(Collections.emptyList());
        // 构造当前用户的基金查询并验证所有业务入口均直接分派
        MyMattersReq fundReq = request("fund", "2");
        service.queryMyMattersPage(fundReq);
        service.queryMyInitiatedMattersPage(fundReq);
        service.queryFlowOptionList(fundReq);
        verify(fund).queryMyMattersPage(fundReq);
        verify(fund).queryMyInitiatedMattersPage(fundReq);
        verify(fund).queryFlowOptionList(fundReq);
        // 构造债券请求，验证不会误入基金分支
        MyMattersReq bondReq = request("bond", "2");
        service.queryMyMattersPage(bondReq);
        service.queryMyInitiatedMattersPage(bondReq);
        service.queryFlowOptionList(bondReq);
        verify(bond).queryMyMattersPage(bondReq);
        verify(bond).queryMyInitiatedMattersPage(bondReq);
        verify(bond).queryFlowOptionList(bondReq);
        verifyNoInteractions(entries);
    }

    /** 缺少业务或未接入的股票及未知业务必须明确拒绝。 */
    @Test public void shouldRejectMissingOrUnsupportedBusinessDomain() {
        for (String domain : new String[]{null, "", " ", "stock", "unknown"}) {
            // 构造错误业务参数，验证全部领域查询均拒绝分派
            MyMattersReq req = request(domain, "2");
            assertThatThrownBy(() -> service.queryMyMattersPage(req)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.queryMyInitiatedMattersPage(req)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.queryFlowOptionList(req)).isInstanceOf(BizException.class);
        }
        verifyNoInteractions(entries, bond, fund);
    }

    /** 入口及事项接口均要求合法的正整数用户主键。 */
    @Test public void shouldRejectInvalidUserIdWithoutQueryingAnyMapper() {
        for (String userId : new String[]{null, "", " ", "abc", "0", "-1", "01", "9223372036854775808"}) {
            // 构造非法用户身份，仅校验格式，不引入业务授权判断
            MyMattersReq req = request("bond", userId);
            assertThatThrownBy(() -> service.queryBusinessDomainList(req)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.queryMyMattersPage(req)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.queryMyInitiatedMattersPage(req)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.queryFlowOptionList(req)).isInstanceOf(BizException.class);
        }
        verifyNoInteractions(entries, bond, fund);
    }

    /** 待处理或已完成之外的列表状态不能进入领域查询。 */
    @Test public void shouldRejectInvalidMatterStatus() {
        // 构造合法业务及用户的查询，只替换错误步骤状态
        MyMattersReq req = request("bond", "2");
        for (String status : new String[]{null, "", "approve"}) {
            req.setStepStatus(status);
            assertThatThrownBy(() -> service.queryMyMattersPage(req)).isInstanceOf(BizException.class);
        }
        verifyNoInteractions(entries, bond, fund);
    }

    /** 构造指定业务和用户的待处理查询请求。 */
    private MyMattersReq request(String domain, String userId) {
        MyMattersReq req = new MyMattersReq();
        req.setBusinessDomain(domain);
        req.setCurrentUserId(userId);
        req.setStepStatus("pending");
        return req;
    }
}

package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.schedule.CrmwRuleAlertScanDto;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.CrmwRuleAlertTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** CRMW 第十八条提醒及人工处理回归测试 */
public class CrmwRuleAlertTaskServiceTest {

    /** 被测提醒服务 */
    private CrmwRuleAlertTaskService service;
    /** 独立提醒数据访问 */
    private CrmwRuleAlertTaskMapper mapper;
    /** 标准评级档位数据访问 */
    private CreditBondGradeRuleMapper grades;
    /** 本轮扫描组合 */
    private CrmwRuleAlertScanDto candidate;

    /** 准备凭证创设方和标准档位，不依赖真实数据库 */
    @Before
    public void setUp() {
        service = new CrmwRuleAlertTaskService();
        mapper = mock(CrmwRuleAlertTaskMapper.class);
        grades = mock(CreditBondGradeRuleMapper.class);
        ReflectionTestUtils.setField(service, "crmwRuleAlertTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "creditBondGradeRuleMapper", grades);
        List<CreditBondInnerRatingGradeBo> list = new ArrayList<>();
        String[] codes = {"1", "2+", "2", "2-", "3+", "3", "3-", "4"};
        for (int i = 0; i < codes.length; i++) {
            CreditBondInnerRatingGradeBo grade = new CreditBondInnerRatingGradeBo();
            grade.setGradeCode(codes[i]);
            grade.setSortNo(i + 1);
            list.add(grade);
        }
        when(grades.queryEnabledRatingGradeList()).thenReturn(list);
        candidate = new CrmwRuleAlertScanDto();
        candidate.setCrmwScode("CRMW1.IB");
        candidate.setCrmwName("测试凭证");
        candidate.setCrmwSecurityType("crmw");
        candidate.setSecurityCode("BOND1.IB");
        candidate.setCurrentPoolId(19L);
        candidate.setAdjustLogId(100L);
        candidate.setIssuerCode("CREATOR");
        candidate.setInnerIssuerRating("3");
        when(mapper.queryCrmwInPoolList()).thenReturn(Collections.singletonList(candidate));
        when(mapper.queryCrmwSecurityList(anyList())).thenReturn(Collections.singletonList(candidate));
        when(mapper.queryLatestIssuerRatingList(anyList())).thenReturn(Collections.singletonList(candidate));
        when(mapper.queryIssuerWatchList(anyList())).thenReturn(Collections.singletonList(candidate));
        when(mapper.queryLatestAlertList(anyList())).thenReturn(Collections.emptyList());
        when(mapper.queryStaleOpenAlertList(anyString())).thenReturn(Collections.emptyList());
    }

    /** 3档及更好档位不因评级产生提醒，3-和4才触发 */
    @Test
    public void shouldUseRatingOrderAndIncludeThreeInSafeRange() {
        for (String grade : Arrays.asList("1", "2+", "2", "2-", "3+", "3")) {
            candidate.setInnerIssuerRating(grade);
            service.execute();
        }
        verify(mapper, never()).addAlert(any());
        for (String grade : Arrays.asList("3-", "4")) {
            candidate.setInnerIssuerRating(grade);
            service.execute();
        }
        verify(mapper, times(2)).addAlert(any());
        assertThat(candidate.getFailReason()).contains("低于3档");
    }

    /** 观察名单独立触发，即使内评为1档也要交研究员处理 */
    @Test
    public void shouldAlertForWatchListsRegardlessOfGoodRating() {
        candidate.setInnerIssuerRating("1");
        candidate.setInObservePool(true);
        candidate.setInRestrictedPool(true);
        service.execute();
        assertThat(candidate.getFailReason()).contains("已在观察名单", "已在重点观察名单").doesNotContain("低于3档");
        verify(mapper).addAlert(candidate);
        verify(mapper).addAlertEvent(any(), eq("0"), eq("INSERT"));
    }

    /** 多项风险同时出现时一条组合提醒保留所有原因 */
    @Test
    public void shouldCombineLowRatingAndWatchReasons() {
        candidate.setInnerIssuerRating("4");
        candidate.setInObservePool(true);
        candidate.setInRestrictedPool(true);
        service.execute();
        assertThat(candidate.getFailReason()).contains("低于3档", "已在观察名单", "已在重点观察名单");
        verify(mapper, times(1)).addAlert(any());
    }

    /** 相同依据已处理时跳过，评级或入库来源变化后重新提醒 */
    @Test
    public void shouldReuseProcessedBasisAndReopenChangedBasis() {
        candidate.setInnerIssuerRating("3-");
        service.execute();
        IpCrmwRuleAlertBo done = new IpCrmwRuleAlertBo();
        done.setAlertStatus("20");
        done.setContextKey(candidate.getContextKey());
        done.setCrmwScode(candidate.getCrmwScode());
        done.setSecurityCode(candidate.getSecurityCode());
        done.setCurrentPoolId(candidate.getCurrentPoolId());
        when(mapper.queryLatestAlertList(anyList())).thenReturn(Collections.singletonList(done));
        service.execute();
        verify(mapper, times(1)).addAlert(any());
        candidate.setInnerIssuerRating("4");
        service.execute();
        verify(mapper, times(2)).addAlert(any());
        done.setContextKey(candidate.getContextKey());
        candidate.setAdjustLogId(101L);
        service.execute();
        verify(mapper, times(3)).addAlert(any());
    }

    /** 待处理记录刷新而不重复新增，合规或出库后的旧待办失效并记审计 */
    @Test
    public void shouldRefreshOpenAndInvalidateStaleAlerts() {
        candidate.setInnerIssuerRating("4");
        IpCrmwRuleAlertBo open = new IpCrmwRuleAlertBo();
        open.setId(8L);
        open.setAlertStatus("00");
        open.setCrmwScode(candidate.getCrmwScode());
        open.setSecurityCode(candidate.getSecurityCode());
        open.setCurrentPoolId(candidate.getCurrentPoolId());
        when(mapper.queryLatestAlertList(anyList())).thenReturn(Collections.singletonList(open));
        IpCrmwRuleAlertBo stale = new IpCrmwRuleAlertBo();
        stale.setId(9L);
        when(mapper.queryStaleOpenAlertList(anyString())).thenReturn(Collections.singletonList(stale));
        when(mapper.editAlertInvalid(stale)).thenReturn(1);
        service.execute();
        verify(mapper).editAlertScan(candidate);
        verify(mapper, never()).addAlert(any());
        verify(mapper).editAlertInvalid(stale);
        verify(mapper).addAlertEvent(8L, "0", "UPDATE");
        verify(mapper).addAlertEvent(9L, "0", "UPDATE");
    }

    /** 缺失凭证主体时不得猜用标的债主体，也不失效旧待办 */
    @Test
    public void shouldFailMissingCreatorWithoutClosingOldAlerts() {
        candidate.setIssuerCode(null);
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("CRMW1.IB", "BOND1.IB");
        assertThat(result.getDetailLog()).contains("任务开始", "扫描条件", "整轮回滚");
        verify(mapper, never()).queryStaleOpenAlertList(anyString());
    }

    /** 最新内评为空或未知档位时明确失败，不能当成低评级或回退旧评分 */
    @Test
    public void shouldRejectMissingAndUnknownRatings() {
        for (String rating : Arrays.asList(null, "", "UNKNOWN")) {
            candidate.setInnerIssuerRating(rating);
            ScheduledTaskResult result = service.execute();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("CREATOR", "最新内评");
        }
        verify(mapper, never()).addAlert(any());
        verify(mapper, never()).queryStaleOpenAlertList(anyString());
    }

    /** 缺失3档边界时任务明确失败 */
    @Test
    public void shouldRejectMissingThresholdConfiguration() {
        when(grades.queryEnabledRatingGradeList()).thenReturn(Collections.emptyList());
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("3档");
        verify(mapper, never()).queryCrmwInPoolList();
    }


    /** 成功结果包含完整起止日志，系统异常转换为带上下文的任务失败结果 */
    @Test
    public void shouldWrapSuccessAndSystemFailureWithTaskLogs() {
        ScheduledTaskResult success = service.execute();
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getTaskCode()).isEqualTo("crmw_rule_alert");
        assertThat(success.getDetailLog()).contains("任务开始", "任务结束", "扫描条件");
        when(mapper.queryCrmwInPoolList()).thenThrow(new IllegalStateException("扫描读取失败"));
        ScheduledTaskResult failure = service.execute();
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.getMessage()).contains("执行异常", "扫描读取失败");
        assertThat(failure.getDetailLog()).contains("任务开始", "整轮回滚", "扫描读取失败");
        verify(mapper, times(1)).queryStaleOpenAlertList(anyString());
    }

    /** 同凭证多标的只批量读取一次风险资料，候选顺序不影响判定 */
    @Test
    public void shouldLoadReferenceDataOnceForRepeatedCredential() {
        candidate.setInnerIssuerRating("4");
        CrmwRuleAlertScanDto other = new CrmwRuleAlertScanDto();
        other.setCrmwScode(candidate.getCrmwScode());
        other.setSecurityCode("BOND2.IB");
        other.setCurrentPoolId(20L);
        other.setAdjustLogId(101L);
        when(mapper.queryCrmwInPoolList()).thenReturn(Arrays.asList(other, candidate));
        ScheduledTaskResult result = service.execute();
        assertThat(result.isSuccess()).isTrue();
        verify(mapper, times(1)).queryCrmwSecurityList(Collections.singletonList("CRMW1.IB"));
        verify(mapper, times(1)).queryLatestIssuerRatingList(Collections.singletonList("CREATOR"));
        verify(mapper, times(1)).queryIssuerWatchList(Collections.singletonList("CREATOR"));
        verify(mapper, times(1)).queryLatestAlertList(Collections.singletonList("CRMW1.IB"));
        verify(mapper, times(2)).addAlert(any());
        assertThat(other.getInnerIssuerRating()).isEqualTo("4");
    }

    /** 无在池组合时不查询大主档和主体资料，仍处理旧提醒失效 */
    @Test
    public void shouldSkipReferenceQueriesForEmptyScan() {
        when(mapper.queryCrmwInPoolList()).thenReturn(Collections.emptyList());
        assertThat(service.execute().isSuccess()).isTrue();
        verify(mapper, never()).queryCrmwSecurityList(anyList());
        verify(mapper, never()).queryLatestIssuerRatingList(anyList());
        verify(mapper, never()).queryIssuerWatchList(anyList());
        verify(mapper, never()).queryLatestAlertList(anyList());
        verify(mapper).queryStaleOpenAlertList(anyString());
    }
}

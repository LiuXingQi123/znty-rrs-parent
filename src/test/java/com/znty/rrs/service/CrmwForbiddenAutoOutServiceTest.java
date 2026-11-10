package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.CrmwForbiddenOutCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwForbiddenAutoOutMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRMW关联禁投四类依据、一次扫描、批量查询和任务结果测试 */
public class CrmwForbiddenAutoOutServiceTest {

    /** 在库扫描数据访问 */
    private CrmwForbiddenAutoOutMapper mapper;
    /** 自动调库日志数据访问 */
    private CrmwPoolAdjustMapper logMapper;
    /** 单组合事务管理器 */
    private PlatformTransactionManager transactionManager;
    /** 本次测试的任务实现 */
    private CrmwForbiddenAutoOutService service;

    /** 准备A发行的标的B1和B机构创设的凭证C1，通过注解事务代理调用真实单条出库方法 */
    @Before
    public void setUp() {
        mapper = mock(CrmwForbiddenAutoOutMapper.class);
        logMapper = mock(CrmwPoolAdjustMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        service = new CrmwForbiddenAutoOutService();
        ReflectionTestUtils.setField(service, "crmwForbiddenAutoOutMapper", mapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", logMapper);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("crmwForbiddenAutoOutService", proxy.getProxy());
        ReflectionTestUtils.setField(service, "selfProvider", beans.getBeanProvider(CrmwForbiddenAutoOutService.class));
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(mapper.queryCrmwInPoolList()).thenReturn(Collections.singletonList(candidate(7L)));
        when(mapper.querySecurityIssuerList(anyList())).thenReturn(Arrays.asList(security("B1", "bond", "A"),
                security("C1", "crmw", "B")));
        when(mapper.editCrmwPoolStatusOut(any())).thenReturn(1);
        when(logMapper.addAdjustLog(any())).thenReturn(1);
    }

    /** 债券、A主体、凭证、B主体任一禁投都能独立清退，且只写CRMW出库日志 */
    @Test
    public void eachForbiddenObjectShouldIndependentlyExitCombination() {
        for (String code : Arrays.asList("B1", "A", "C1", "B")) {
            setUp();
            when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Collections.singletonList(code));

            ScheduledTaskResult result = service.execute();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(1);
            ArgumentCaptor<IpAdjustLogBo> logs = ArgumentCaptor.forClass(IpAdjustLogBo.class);
            verify(logMapper).addAdjustLog(logs.capture());
            IpAdjustLogBo log = logs.getValue();
            assertThat(log.getAdjustReason()).contains("[" + code + "]已在禁止库");
            assertThat(log.getAdjustMode()).isEqualTo("调出");
            assertThat(log.getAdjustType()).isEqualTo("自动调整");
            assertThat(log.getAuditStatus()).isEqualTo("20");
            assertThat(log.getAdjusterId()).isEqualTo("0");
            assertThat(log.getAdjusterName()).isEqualTo("系统");
            assertThat(log.getPoolType()).isEqualTo("crmw");
            assertThat(log.getAdjustBatchNo()).startsWith("CRMW");
            assertThat(log.getAdjustAdvice()).isNull();
            assertThat(result.getDetailLog()).contains("扫描条件：", "任务结束：").doesNotContain(log.getAdjustBatchNo());
        }
    }

    /** 四类依据同时成立只退一次，原因全部保留并按实际代码批量查询 */
    @Test
    public void allReasonsShouldMergeIntoOneExit() {
        when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Arrays.asList("B1", "A", "C1", "B"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.getAffectedCount()).isEqualTo(1);
        ArgumentCaptor<IpAdjustLogBo> logs = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(logMapper).addAdjustLog(logs.capture());
        assertThat(logs.getValue().getAdjustReason()).contains("标的债券[B1]", "标的发行主体[A]",
                "CRMW凭证[C1]", "创设方／保护方[B]");
        verify(mapper).querySecurityIssuerList(Arrays.asList("B1", "C1"));
        verify(mapper).queryForbiddenCodeList(argThat(values -> values.size() == 4
                && values.containsAll(Arrays.asList("B1", "C1", "A", "B"))));
    }

    /** 没有有效组合时不查询证券主档和禁止库 */
    @Test
    public void emptyPoolShouldSkipReferenceQueries() {
        when(mapper.queryCrmwInPoolList()).thenReturn(Collections.emptyList());

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        verify(mapper, never()).querySecurityIssuerList(anyList());
        verify(mapper, never()).queryForbiddenCodeList(anyList());
        verify(logMapper, never()).addAdjustLog(any());
    }

    /** 没命中禁止库时不改池，不将其他风险状态推断为禁投 */
    @Test
    public void noForbiddenMatchShouldKeepCombination() {
        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        verify(mapper, never()).editCrmwPoolStatusOut(any());
        verify(logMapper, never()).addAdjustLog(any());
    }

    /** 原组合已出库时不重复生成业务日志 */
    @Test
    public void alreadyExitedRowShouldNotWriteLog() {
        when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Collections.singletonList("B"));
        when(mapper.editCrmwPoolStatusOut(any())).thenReturn(0);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(result.getDetailLog()).contains("原组合已出库");
        verify(logMapper, never()).addAdjustLog(any());
    }

    /** 缺少证券主档或唯一主体代码时记录该条错误，不推断发行人且任务继续完成 */
    @Test
    public void incompleteMasterShouldFailBeforeMutation() {
        List<List<SecurityInfoBo>> invalidMasters = Arrays.asList(
                Collections.singletonList(security("B1", "bond", "A")),
                Arrays.asList(security("B1", "bond", null), security("C1", "crmw", "B")),
                Arrays.asList(security("B1", "bond", "A"), security("C1", "crmw", "B,C")),
                Arrays.asList(security("B1", "bond", "A"), security("C1", "bond", "B")));
        for (List<SecurityInfoBo> masters : invalidMasters) {
            when(mapper.querySecurityIssuerList(anyList())).thenReturn(masters);
            ScheduledTaskResult result = service.execute();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("失败 1 条");
            assertThat(result.getDetailLog()).contains("保留原组合并继续", "CRMW[C1]标的[B1]池[18]");
        }
        verify(mapper, never()).editCrmwPoolStatusOut(any());
        verify(logMapper, never()).addAdjustLog(any());
    }

    /** 全部组合一次读取，重复证券只查询一次主档，每个命中组合独立提交 */
    @Test
    public void allCombinationsShouldUseOneScanAndIndependentTransactions() {
        when(mapper.queryCrmwInPoolList()).thenReturn(Arrays.asList(candidate(7L), candidate(9L), candidate(21L)));
        when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Collections.singletonList("B"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(3);
        verify(mapper).queryCrmwInPoolList();
        verify(mapper).querySecurityIssuerList(Arrays.asList("B1", "C1"));
        verify(transactionManager, times(3)).commit(any());
    }

    /** 中间一条日志失败只回滚该条，前后组合均继续成功提交 */
    @Test
    public void failedLogShouldRollbackOnlyCurrentCombinationAndContinue() {
        when(mapper.queryCrmwInPoolList()).thenReturn(Arrays.asList(candidate(7L), candidate(9L), candidate(21L)));
        when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Collections.singletonList("B"));
        when(logMapper.addAdjustLog(any())).thenReturn(1).thenThrow(new BizException("模拟日志写入失败")).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("模拟日志写入失败", "保留原组合并继续");
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager).rollback(any());
        verify(logMapper, times(3)).addAdjustLog(any());
    }

    /** 中间一条主档缺失不阻断后面的正常组合 */
    @Test
    public void invalidMasterShouldNotBlockOtherCombinations() {
        CrmwForbiddenOutCandidateDto invalid = candidate(9L);
        invalid.setSecurityCode("MISSING");
        when(mapper.queryCrmwInPoolList()).thenReturn(Arrays.asList(candidate(7L), invalid, candidate(21L)));
        when(mapper.queryForbiddenCodeList(anyList())).thenReturn(Collections.singletonList("B"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("标的[MISSING]", "主档缺失");
        verify(mapper, times(2)).editCrmwPoolStatusOut(any());
    }

    /** 无法完成公共查询时返回任务级失败，不进行没有依据的清退 */
    @Test
    public void sharedQueryFailureShouldReturnTaskFailure() {
        when(mapper.querySecurityIssuerList(anyList())).thenThrow(new BizException("主档查询不可用"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("公共查询失败", "主档查询不可用");
        verify(mapper, never()).editCrmwPoolStatusOut(any());
    }

    /** 构建单个在库组合的任务测试数据 */
    private CrmwForbiddenOutCandidateDto candidate(long id) {
        CrmwForbiddenOutCandidateDto row = new CrmwForbiddenOutCandidateDto();
        row.setPoolStatusId(id);
        row.setSecurityCode("B1");
        row.setSecurityShortName("A公司债");
        row.setSecurityType("bond");
        row.setCrmwScode("C1");
        row.setCrmwName("B机构凭证");
        row.setCrmwStype("crmw");
        row.setTargetPoolId(18L);
        row.setTargetPoolName("CRMW库");
        return row;
    }

    /** 构建证券及自身发行主体数据 */
    private SecurityInfoBo security(String code, String type, String issuerCode) {
        SecurityInfoBo row = new SecurityInfoBo();
        row.setWindCode(code);
        row.setSecurityType(type);
        row.setIssuerCode(issuerCode);
        return row;
    }
}

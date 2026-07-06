package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 禁投池主体调整审批服务测试。 */
public class ForbiddenPoolAdjustFlowServiceTest {

    /** 验证审批最终通过后主体和实际变化的旗下债券一并入池。 */
    @Test
    public void finishAdjustBatchShouldSyncCompanyAndChangedBonds() {
        ForbiddenPoolAdjustMapper mapper = mock(ForbiddenPoolAdjustMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        InvestmentPoolService poolService = mock(InvestmentPoolService.class);
        ForbiddenPoolAdjustFlowService service = new ForbiddenPoolAdjustFlowService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(service, "investmentPoolService", poolService);
        IpAdjustLogBo companyLog = buildCompanyLog();
        SecurityInfoBo bond = new SecurityInfoBo();
        bond.setWindCode("B001");
        bond.setShortName("测试债券");
        bond.setSecurityType("company_bond");
        when(mapper.queryAdjustLogListForAudit(10L, "COMPANY202606281001"))
                .thenReturn(Collections.singletonList(companyLog));
        when(mapper.queryCategoryTypeBySecurityType("company")).thenReturn("company");
        when(mapper.queryCompanyBondForAutoList("C10001")).thenReturn(Collections.singletonList(bond));
        when(mapper.querySecurityCurrentPoolIdList("B001")).thenReturn(Collections.<Long>emptyList());
        when(poolService.queryPoolFullNameMap()).thenReturn(Collections.<Long, String>emptyMap());
        when(attachmentService.queryHandCreditReportAttachments(10L)).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            IpAdjustLogBo log = (IpAdjustLogBo) invocation.getArguments()[0];
            log.setId(11L);
            return 1;
        }).when(mapper).addAdjustLog(any(IpAdjustLogBo.class));
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(10L);
        step.setAdjustBatchNo("COMPANY202606281001");

        ReflectionTestUtils.invokeMethod(service, "finishAdjustBatch", step);

        ArgumentCaptor<IpAdjustLogBo> statusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper, org.mockito.Mockito.times(2)).addPoolStatus(statusCaptor.capture());
        List<IpAdjustLogBo> statusLogs = statusCaptor.getAllValues();
        assertThat(statusLogs).extracting(IpAdjustLogBo::getSecurityCode)
                .containsExactly("C10001", "B001");
        ArgumentCaptor<IpAdjustLogBo> autoLogCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(mapper).addAdjustLog(autoLogCaptor.capture());
        assertThat(autoLogCaptor.getValue().getAdjustType()).isEqualTo("自动调整");
        assertThat(autoLogCaptor.getValue().getAdjustBatchNo()).isEqualTo(companyLog.getAdjustBatchNo());
        verify(mapper, never()).addAdjustStep(any(IpAdjustStepBo.class));
    }

    /** 构建主体调入日志。 */
    private IpAdjustLogBo buildCompanyLog() {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setId(10L);
        log.setSecurityCode("C10001");
        log.setSecurityShortName("某公司");
        log.setSecurityType("company");
        log.setAdjustMode("调入");
        log.setAdjustBatchNo("COMPANY202606281001");
        log.setTargetPoolId(15L);
        log.setTargetPoolName("禁投池");
        log.setPoolType("forbidden");
        log.setAdjusterId("1");
        log.setAdjusterName("管理员");
        return log;
    }
}

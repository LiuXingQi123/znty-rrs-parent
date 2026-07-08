package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动调库定时任务服务单元测试。
 *
 * <p>覆盖到期出池规则：到期证券触发 addAdjustLog + deletePoolStatusSoft；无配置池时跳过。
 */
public class AutoAdjustServiceTest {

    /** 到期出池：配置了 auto_out 的池中有到期证券时，应写自动调出日志并软删除池状态。 */
    @Test
    public void autoOutExpiredShouldDeleteExpiredSecurities() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);

        // 配 auto_out 池
        when(autoAdjustMapper.queryAutoOutPoolIds()).thenReturn(Arrays.asList(10L));
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(10L);
        pool.setPoolName("信用债大库");
        pool.setPoolType("credit_bond");
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(pool));
        // 到期证券
        IpAdjustLogBo expired = new IpAdjustLogBo();
        expired.setSecurityCode("S001");
        expired.setSecurityShortName("测试债");
        expired.setSecurityType("corporate_bond");
        when(autoAdjustMapper.queryPoolSecurityByExpired(10L)).thenReturn(Arrays.asList(expired));

        service.executeAutoAdjust();

        // 验证写自动调出日志 + 软删除池状态
        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(securityPoolAdjustMapper).addAdjustLog(logCaptor.capture());
        verify(securityPoolAdjustMapper).deletePoolStatusSoft("S001", 10L);
        IpAdjustLogBo log = logCaptor.getValue();
        assertThat(log.getAdjustType()).isEqualTo("自动调整");
        assertThat(log.getAdjustMode()).isEqualTo(AdjustMode.OUT.getCode());
        assertThat(log.getAuditStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(log.getAdjusterName()).isEqualTo("系统");
        assertThat(log.getAdjustReason()).isEqualTo("证券到期自动调出");
        assertThat(log.getTargetPoolId()).isEqualTo(10L);
        assertThat(log.getAdjustBatchNo()).startsWith("AUTO");
    }

    /** 到期出池：无配置 auto_out 规则的池时应跳过，不写日志不删池状态。 */
    @Test
    public void autoOutExpiredShouldSkipWhenNoAutoOutPool() {
        AutoAdjustMapper autoAdjustMapper = mock(AutoAdjustMapper.class);
        SecurityPoolAdjustMapper securityPoolAdjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        AutoAdjustService service = new AutoAdjustService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityPoolAdjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);

        when(autoAdjustMapper.queryAutoOutPoolIds()).thenReturn(Collections.<Long>emptyList());

        service.executeAutoAdjust();

        verify(securityPoolAdjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(securityPoolAdjustMapper, never()).deletePoolStatusSoft(any(String.class), any(Long.class));
    }
}

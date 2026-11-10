package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwRuleAlertMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CRMW提醒页面人工处理测试，不执行定时扫描 */
public class CrmwRuleAlertServiceTest {

    /** 人工处理只更新提醒，保留本次依据并记录操作人 */
    @Test
    public void shouldMarkProcessedAndKeepBasis() {
        CrmwRuleAlertService service = new CrmwRuleAlertService();
        CrmwRuleAlertMapper mapper = mock(CrmwRuleAlertMapper.class);
        ReflectionTestUtils.setField(service, "crmwRuleAlertMapper", mapper);
        IpCrmwRuleAlertBo candidate = new IpCrmwRuleAlertBo();
        candidate.setId(8L);
        candidate.setAlertStatus("00");
        candidate.setContextKey("basis");
        when(mapper.queryAlertById(8L)).thenReturn(candidate);
        when(mapper.editAlertProcessed(any())).thenReturn(1);
        CrmwRuleAlertReq req = new CrmwRuleAlertReq();
        req.setId(8L);
        req.setCurrentUserId("2");
        req.setCurrentUserName("研究员");
        assertThat(service.editAlertProcessed(req).getAlertStatus()).isEqualTo("20");
        ArgumentCaptor<IpCrmwRuleAlertBo> captured = ArgumentCaptor.forClass(IpCrmwRuleAlertBo.class);
        verify(mapper).editAlertProcessed(captured.capture());
        assertThat(captured.getValue().getContextKey()).isEqualTo("basis");
        verify(mapper).addAlertEvent(8L, "2", "UPDATE");
        assertThatThrownBy(() -> service.editAlertProcessed(req)).isInstanceOf(BizException.class);
    }
}

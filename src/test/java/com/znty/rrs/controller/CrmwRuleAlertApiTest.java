package com.znty.rrs.controller;

import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import com.znty.rrs.service.CrmwRuleAlertService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 独立 CRMW 提醒接口与组合筛选字段回归测试 */
public class CrmwRuleAlertApiTest extends ControllerApiTestSupport {

    /** 真实路由绑定独立服务，筛选字段及处理人完整传入 */
    @Test
    public void shouldExposeIndependentReminderEndpoints() throws Exception {
        CrmwRuleAlertController controller = new CrmwRuleAlertController();
        CrmwRuleAlertService service = mock(CrmwRuleAlertService.class);
        ReflectionTestUtils.setField(controller, "crmwRuleAlertService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        assertPostSuccess(mvc, "/api/v1/crmwRuleAlert/queryAlertPage",
                "{\"crmwScode\":\"C1\",\"securityCode\":\"B1\",\"alertStatus\":\"00\",\"pageIndex\":2,\"pageSize\":10}");
        ArgumentCaptor<CrmwRuleAlertReq> captured = ArgumentCaptor.forClass(CrmwRuleAlertReq.class);
        verify(service).queryAlertPage(captured.capture());
        assertThat(captured.getValue().getCrmwScode()).isEqualTo("C1");
        assertThat(captured.getValue().getSecurityCode()).isEqualTo("B1");
        assertThat(captured.getValue().getPageIndex()).isEqualTo(2);
        assertPostSuccess(mvc, "/api/v1/crmwRuleAlert/editAlertProcessed",
                "{\"id\":1,\"currentUserId\":\"2\",\"currentUserName\":\"研究员\"}");
        verify(service).editAlertProcessed(captured.capture());
        assertThat(captured.getValue().getCurrentUserId()).isEqualTo("2");
    }
}

package com.znty.rrs.controller;

import com.znty.rrs.entity.mymatters.BusinessDomainDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.service.MyMattersService;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的事宜页面接口测试
 */
public class MyMattersApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 我的事宜服务。 */
    private MyMattersService myMattersService;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        MyMattersController controller = new MyMattersController();
        myMattersService = mock(MyMattersService.class);
        ReflectionTestUtils.setField(controller, "myMattersService", myMattersService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportMatterQueryAndFiltering 测试场景。 */
    @Test
    public void shouldSupportMatterQueryAndFiltering() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryMyMattersPage",
                "{\"businessDomain\":\"bond\",\"stepStatus\":\"pending\",\"currentUserId\":\"1\",\"securityCode\":\"CRMW22001.IB\",\"securityShortName\":\"某电力\"}");

        ArgumentCaptor<MyMattersReq> captor = ArgumentCaptor.forClass(MyMattersReq.class);
        verify(myMattersService).queryMyMattersPage(captor.capture());
        MyMattersReq req = captor.getValue();
        assertThat(req.getSecurityCode()).isEqualTo("CRMW22001.IB");
        assertThat(req.getSecurityShortName()).isEqualTo("某电力");
        assertThat(req.getBusinessDomain()).isEqualTo("bond");

        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryFlowOptionList", "{\"businessDomain\":\"bond\",\"currentUserId\":\"1\"}");
        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryMyInitiatedMattersPage",
                "{\"businessDomain\":\"fund\",\"currentUserId\":\"2\",\"pageIndex\":1,\"pageSize\":20}");
    }

    /** 业务选项接口只返回可见入口编码，不再返回管理能力。 */
    @Test
    public void shouldSupportBusinessDomainOptions() throws Exception {
        BusinessDomainDto bond = new BusinessDomainDto();
        bond.setBusinessDomain("bond");
        BusinessDomainDto fund = new BusinessDomainDto();
        fund.setBusinessDomain("fund");
        when(myMattersService.queryBusinessDomainList(any(MyMattersReq.class)))
                .thenReturn(Arrays.asList(bond, fund));
        // 发送入口请求并验证响应中仅保留业务编码
        postJson(mockMvc, "/api/v1/myMatters/queryBusinessDomainList", "{\"currentUserId\":\"2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].businessDomain").value("bond"))
                .andExpect(jsonPath("$.data[1].businessDomain").value("fund"))
                .andExpect(jsonPath("$.data[0].canManage").doesNotExist())
                .andExpect(jsonPath("$.data[1].canManage").doesNotExist());
        ArgumentCaptor<MyMattersReq> captor = ArgumentCaptor.forClass(MyMattersReq.class);
        verify(myMattersService).queryBusinessDomainList(captor.capture());
        assertThat(captor.getValue().getCurrentUserId()).isEqualTo("2");
    }
}

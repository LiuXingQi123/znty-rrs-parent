package com.znty.rrs.controller;

import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.service.MyMattersService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
                "{\"currentUserId\":\"1\",\"securityCode\":\"CRMW22001.IB\",\"securityShortName\":\"某电力\"}");

        ArgumentCaptor<MyMattersReq> captor = ArgumentCaptor.forClass(MyMattersReq.class);
        verify(myMattersService).queryMyMattersPage(captor.capture());
        MyMattersReq req = captor.getValue();
        assertThat(req.getSecurityCode()).isEqualTo("CRMW22001.IB");
        assertThat(req.getSecurityShortName()).isEqualTo("某电力");

        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryFlowOptionList", "{\"currentUserId\":\"1\"}");
    }
}

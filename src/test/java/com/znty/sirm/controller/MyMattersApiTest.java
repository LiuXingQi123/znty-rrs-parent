package com.znty.sirm.controller;

import com.znty.sirm.service.MyMattersService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 我的事宜页面接口测试
 */
public class MyMattersApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        MyMattersController controller = new MyMattersController();
        ReflectionTestUtils.setField(controller, "myMattersService", mock(MyMattersService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportMatterQueryAndFiltering 测试场景。 */
    @Test
    public void shouldSupportMatterQueryAndFiltering() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryMyMattersPage", "{\"handlerId\":\"1001\"}");
        assertPostSuccess(mockMvc, "/api/v1/myMatters/queryFlowOptionList", "{\"handlerId\":\"1001\"}");
    }
}

package com.znty.sirm.controller;

import com.znty.sirm.service.ForbiddenPoolQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 禁投池查询页面接口测试
 */
public class ForbiddenPoolQueryApiTest extends ControllerApiTestSupport {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        ForbiddenPoolQueryController controller = new ForbiddenPoolQueryController();
        ReflectionTestUtils.setField(controller, "forbiddenPoolQueryService", mock(ForbiddenPoolQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldSupportForbiddenPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/forbiddenPoolQuery/queryForbiddenPoolPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenPoolQuery/querySecurityTypeList");
    }
}

package com.znty.rrs.controller;

import com.znty.rrs.service.ForbiddenPoolHistoryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/** 禁投池历史查询页面接口测试。 */
public class ForbiddenPoolHistoryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        ForbiddenPoolHistoryController controller = new ForbiddenPoolHistoryController();
        ReflectionTestUtils.setField(controller, "forbiddenPoolHistoryService", mock(ForbiddenPoolHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证主体条件和批次号相关的历史查询接口可正常受理。 */
    @Test
    public void shouldSupportForbiddenPoolHistoryQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/forbiddenPoolHistory/queryForbiddenPoolHistoryPage",
                "{\"companyCode\":\"C10005\",\"companyName\":\"演示主体\"}");
    }
}

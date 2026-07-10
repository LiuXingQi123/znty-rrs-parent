package com.znty.rrs.controller;

import com.znty.rrs.service.ForbiddenPoolQueryService;
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

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        ForbiddenPoolQueryController controller = new ForbiddenPoolQueryController();
        ReflectionTestUtils.setField(controller, "forbiddenPoolQueryService", mock(ForbiddenPoolQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportForbiddenPoolQuery 测试场景。 */
    @Test
    public void shouldSupportForbiddenPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/forbiddenPoolQuery/queryForbiddenPoolPage",
                "{\"securityStatus\":\"active\",\"entryTimeStart\":\"2026-07-01\",\"entryTimeEnd\":\"2026-07-10\"}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenPoolQuery/querySecurityTypeList", "{}");
    }
}

package com.znty.rrs.controller;

import com.znty.rrs.service.SecurityPoolAdjustHistoryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池调整历史页面接口测试
 */
public class SecurityPoolAdjustHistoryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolAdjustHistoryController controller = new SecurityPoolAdjustHistoryController();
        ReflectionTestUtils.setField(controller, "securityPoolAdjustHistoryService",
                mock(SecurityPoolAdjustHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证证券池调整历史查询接口。 */
    @Test
    public void shouldSupportSecurityPoolAdjustHistoryQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjustHistory/querySecurityPoolAdjustHistoryPage", "{}");
        assertPostSuccess(
                mockMvc,
                "/api/v1/securityPoolAdjustHistory/querySecurityPoolAdjustHistoryPage",
                "{\"poolIds\":[2,3],\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjustHistory/querySecurityTypeList", "{}");
    }
}

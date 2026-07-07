package com.znty.rrs.controller;

import com.znty.rrs.service.AdjustHistoryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池调整历史页面接口测试
 */
public class AdjustHistoryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        AdjustHistoryController controller = new AdjustHistoryController();
        ReflectionTestUtils.setField(controller, "adjustHistoryService", mock(AdjustHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportAdjustHistoryQuery 测试场景。 */
    @Test
    public void shouldSupportAdjustHistoryQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/adjustHistory/queryAdjustHistoryPage", "{}");
        assertPostSuccess(
                mockMvc,
                "/api/v1/adjustHistory/queryAdjustHistoryPage",
                "{\"poolIds\":[2,3],\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/adjustHistory/querySecurityTypeList", "{}");
    }
}

package com.znty.sirm.controller;

import com.znty.sirm.service.AdjustHistoryService;
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

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        AdjustHistoryController controller = new AdjustHistoryController();
        ReflectionTestUtils.setField(controller, "adjustHistoryService", mock(AdjustHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldSupportAdjustHistoryQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/adjustHistory/queryAdjustHistoryPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/adjustHistory/querySecurityTypeList");
        assertPostSuccess(mockMvc, "/api/v1/adjustHistory/queryPoolTreeList", "{}");
    }
}

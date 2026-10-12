package com.znty.rrs.controller;

import com.znty.rrs.service.ForbiddenAbsPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * ABS 禁投池调整申请接口测试。
 */
public class ForbiddenAbsPoolAdjustApiTest extends ControllerApiTestSupport {

    /** ABS 禁投调整接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化 MockMvc */
    @Before
    public void setUp() {
        ForbiddenAbsPoolAdjustController controller = new ForbiddenAbsPoolAdjustController();
        ReflectionTestUtils.setField(controller, "forbiddenAbsPoolAdjustService",
                mock(ForbiddenAbsPoolAdjustService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 ABS 债查询、池状态接口 */
    @Test
    public void shouldSupportAbsSecurityQueryAndPoolStatus() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/querySecurityDetail",
                "{\"securityCode\":\"108008901.IB\"}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/querySecurityPoolStatus",
                "{\"securityCode\":\"108008901.IB\"}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/queryAdjustPoolList",
                "{\"securityCode\":\"108008901.IB\",\"adjustDirection\":\"in\"}");
    }

    /** 验证校验、提交、记录接口 */
    @Test
    public void shouldSupportAbsAdjustCheckAndSubmit() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/addAdjustLog", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/queryAdjustLogList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/queryAdjustStepList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/forbiddenAbsPoolAdjust/queryLastCreditReport",
                "{\"securityCode\":\"108008901.IB\"}");
    }
}

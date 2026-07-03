package com.znty.rrs.controller;

import com.znty.rrs.service.SecurityPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池调库详情页面接口测试
 */
public class SecurityPoolAdjustDetailApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolAdjustController controller = new SecurityPoolAdjustController();
        ReflectionTestUtils.setField(controller, "securityPoolAdjustService", mock(SecurityPoolAdjustService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportCompleteAdjustDetailLoading 测试场景。 */
    @Test
    public void shouldSupportCompleteAdjustDetailLoading() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    /** 验证 shouldSupportDetailPageValidationAndResubmission 测试场景。 */
    @Test
    public void shouldSupportDetailPageValidationAndResubmission() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/addAdjustLog", "{}");
    }
}

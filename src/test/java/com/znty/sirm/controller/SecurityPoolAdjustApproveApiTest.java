package com.znty.sirm.controller;

import com.znty.sirm.service.SecurityPoolAdjustFlowService;
import com.znty.sirm.service.SecurityPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池调库审核页面接口测试
 */
public class SecurityPoolAdjustApproveApiTest extends ControllerApiTestSupport {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        SecurityPoolAdjustController adjustController = new SecurityPoolAdjustController();
        SecurityPoolAdjustFlowController flowController = new SecurityPoolAdjustFlowController();
        ReflectionTestUtils.setField(adjustController, "securityPoolAdjustService", mock(SecurityPoolAdjustService.class));
        ReflectionTestUtils.setField(flowController, "securityPoolAdjustFlowService", mock(SecurityPoolAdjustFlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(adjustController, flowController).build();
    }

    @Test
    public void shouldSupportApprovalContextQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    @Test
    public void shouldSupportApprovalDecision() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjustFlow/submitAdjustAudit",
                "{\"stepId\":1,\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\",\"processAction\":\"approve\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/addAdjustLog", "{}");
    }
}

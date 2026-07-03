package com.znty.rrs.controller;

import com.znty.rrs.service.CrmwPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * CRMW池调整申请页面接口测试
 */
public class CrmwPoolAdjustApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        CrmwPoolAdjustController controller = new CrmwPoolAdjustController();
        ReflectionTestUtils.setField(controller, "crmwPoolAdjustService", mock(CrmwPoolAdjustService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 CRMW 与标的证券选择接口。 */
    @Test
    public void shouldSupportCrmwAndSecuritySelection() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryBindableSecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwDetail", "{\"securityCode\":\"CRMW24001.IB\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
    }

    /** 验证 CRMW 调整选择、校验与提交接口。 */
    @Test
    public void shouldSupportCrmwAdjustValidationAndSubmission() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/checkCrmwAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/addCrmwAdjustLog", "{}");
    }

    /** 验证 CRMW 调整追踪接口。 */
    @Test
    public void shouldSupportCrmwAdjustTrace() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"CRMW202606130001\"}");
    }
}

package com.znty.rrs.controller;

import com.znty.rrs.service.ForbiddenPoolAdjustFlowService;
import com.znty.rrs.service.ForbiddenPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/** 禁投池主体调整接口测试。 */
public class ForbiddenPoolAdjustApiTest extends ControllerApiTestSupport {

    /** 主体调整接口测试客户端。 */
    private MockMvc adjustMockMvc;

    /** 审批接口测试客户端。 */
    private MockMvc flowMockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        ForbiddenPoolAdjustController adjustController = new ForbiddenPoolAdjustController();
        ReflectionTestUtils.setField(adjustController, "forbiddenPoolAdjustService",
                mock(ForbiddenPoolAdjustService.class));
        adjustMockMvc = MockMvcBuilders.standaloneSetup(adjustController).build();
        ForbiddenPoolAdjustFlowController flowController = new ForbiddenPoolAdjustFlowController();
        ReflectionTestUtils.setField(flowController, "forbiddenPoolAdjustFlowService",
                mock(ForbiddenPoolAdjustFlowService.class));
        flowMockMvc = MockMvcBuilders.standaloneSetup(flowController).build();
    }

    /** 验证主体查询、池状态和旗下债券接口。 */
    @Test
    public void shouldSupportCompanyQueryAndPoolStatus() throws Exception {
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryCompanyPage", "{}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryCompanyDetail",
                "{\"companyCode\":\"C10001\"}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryCompanyPoolStatus",
                "{\"companyCode\":\"C10001\"}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryCompanyBondList",
                "{\"companyCode\":\"C10001\",\"targetPoolId\":15}");
    }

    /** 验证调库校验、提交、记录和审批接口。 */
    @Test
    public void shouldSupportCompanyAdjustAndAudit() throws Exception {
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryAdjustPoolList", "{}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/addAdjustLog", "{}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryAdjustLogList", "{}");
        assertPostSuccess(adjustMockMvc, "/api/v1/forbiddenPoolAdjust/queryAdjustStepList", "{}");
        assertPostSuccess(flowMockMvc, "/api/v1/forbiddenPoolAdjustFlow/submitAdjustAudit", "{}");
    }
}

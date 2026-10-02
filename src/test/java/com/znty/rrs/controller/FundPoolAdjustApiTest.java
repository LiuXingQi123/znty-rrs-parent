package com.znty.rrs.controller;

import com.znty.rrs.service.FundPoolAdjustService;
import com.znty.rrs.service.FundPoolAdjustFlowService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/** 基金池调整申请接口测试 */
public class FundPoolAdjustApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        FundPoolAdjustController controller = new FundPoolAdjustController();
        ReflectionTestUtils.setField(controller, "fundPoolAdjustService", mock(FundPoolAdjustService.class));
        FundPoolAdjustFlowController flowController = new FundPoolAdjustFlowController();
        ReflectionTestUtils.setField(flowController, "fundPoolAdjustFlowService", mock(FundPoolAdjustFlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller, flowController).build();
    }

    /** 验证基金查询与选池接口 */
    @Test
    public void shouldSupportFundSelectionAndPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryFundPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryFundTypeList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryFundDetail", "{\"fundCode\":\"FUND001.SH\"}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryAdjustPoolList",
                "{\"fundCode\":\"FUND001.SH\",\"adjustDirection\":\"in\",\"currentUserId\":\"1\"}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryFundPoolStatus",
                "{\"fundCode\":\"FUND001.SH\"}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryAdjustLogList",
                "{\"fundCode\":\"FUND001.SH\",\"adjustBatchNo\":\"FUND-BATCH001\"}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/queryAdjustStepList",
                "{\"adjustLogId\":1,\"adjustBatchNo\":\"FUND-BATCH001\"}");
    }

    /** 验证基金校验和 JSON 提交接口 */
    @Test
    public void shouldSupportFundValidationAndJsonSubmission() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/checkAdjust",
                "{\"fundCode\":\"FUND001.SH\",\"items\":[]}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/addAdjustLog",
                "{\"fundCode\":\"FUND001.SH\",\"fundScore\":8.5,\"fundInvestmentType\":\"stock\",\"items\":[]}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjust/submitAdjustAudit",
                "{\"stepId\":1,\"adjustLogId\":1,\"adjustBatchNo\":\"FUND-BATCH001\",\"processAction\":\"approve\",\"handlerId\":\"1\"}");
    }
}

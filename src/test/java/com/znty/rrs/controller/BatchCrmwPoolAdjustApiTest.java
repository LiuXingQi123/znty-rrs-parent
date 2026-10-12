package com.znty.rrs.controller;

import com.znty.rrs.service.BatchCrmwPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * CRMW 池批量调整接口测试
 */
public class BatchCrmwPoolAdjustApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        BatchCrmwPoolAdjustController controller = new BatchCrmwPoolAdjustController();
        ReflectionTestUtils.setField(
                controller,
                "batchCrmwPoolAdjustService",
                mock(BatchCrmwPoolAdjustService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证批量调整投资池分页接口 */
    @Test
    public void shouldQueryBatchAdjustPoolPage() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/queryPoolPage",
                "{\"currentUserId\":\"1\",\"poolIds\":[18],\"pageIndex\":1,\"pageSize\":10}");
    }

    /** 验证批量调入候选组合分页接口 */
    @Test
    public void shouldQueryInboundCandidatePage() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/queryInboundCandidatePage",
                "{\"poolId\":18,\"bondYesFlags\":[\"abs\",\"guarant\"],\"pageIndex\":1,\"pageSize\":20}");
    }

    /** 验证批量调出候选组合分页接口 */
    @Test
    public void shouldQueryOutboundCandidatePage() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/queryOutboundCandidatePage",
                "{\"poolId\":18,\"bondYesFlags\":[\"yx\",\"private\"],\"pageIndex\":1,\"pageSize\":20}");
    }

    /** 验证批量调库下一步校验接口支持调入 */
    @Test
    public void shouldCheckBatchInboundAdjust() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1\",\"direction\":\"in\",\"poolId\":18,\"securities\":[{\"securityCode\":\"MTN001.IB\",\"crmwScode\":\"CRMW001.IB\"}]}");
    }

    /** 验证批量调库下一步校验接口支持调出 */
    @Test
    public void shouldCheckBatchOutboundAdjust() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1\",\"direction\":\"out\",\"poolId\":18,\"securities\":[{\"securityCode\":\"MTN001.IB\",\"crmwScode\":\"CRMW001.IB\"}]}");
    }

    /** 验证批量调库提交接口支持调入 */
    @Test
    public void shouldAddBatchInboundAdjustLog() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1\",\"direction\":\"in\",\"adjusterId\":\"1\",\"adjusterName\":\"管理员\",\"poolId\":18,\"items\":[{\"securityCode\":\"MTN001.IB\",\"crmwScode\":\"CRMW001.IB\",\"targetPoolId\":18,\"adjustMode\":\"调入\",\"flowId\":1}]}");
    }

    /** 验证批量调库提交接口支持调出 */
    @Test
    public void shouldAddBatchOutboundAdjustLog() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchCrmwPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1\",\"direction\":\"out\",\"adjusterId\":\"1\",\"adjusterName\":\"管理员\",\"poolId\":18,\"items\":[{\"securityCode\":\"MTN001.IB\",\"crmwScode\":\"CRMW001.IB\",\"targetPoolId\":18,\"adjustMode\":\"调出\",\"flowId\":1}]}");
    }
}

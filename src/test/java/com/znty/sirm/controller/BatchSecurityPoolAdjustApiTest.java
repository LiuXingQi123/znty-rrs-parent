package com.znty.sirm.controller;

import com.znty.sirm.service.BatchSecurityPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池批量调整接口测试
 */
public class BatchSecurityPoolAdjustApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        BatchSecurityPoolAdjustController controller = new BatchSecurityPoolAdjustController();
        ReflectionTestUtils.setField(
                controller,
                "batchSecurityPoolAdjustService",
                mock(BatchSecurityPoolAdjustService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证批量调整投资池列表接口 */
    @Test
    public void shouldQueryBatchAdjustPoolList() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/queryPoolList",
                "{\"currentUserId\":\"1001\"}");
    }

    /** 验证批量调整候选证券分页接口 */
    @Test
    public void shouldQueryBatchAdjustSecurityPage() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/querySecurityPage",
                "{\"poolId\":2,\"direction\":\"in\",\"pageIndex\":1,\"pageSize\":20}");
    }

    /** 验证批量调库下一步校验接口支持调入 */
    @Test
    public void shouldCheckBatchInboundAdjust() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1001\",\"direction\":\"in\",\"poolId\":2,\"securities\":[{\"securityCode\":\"100001\"}]}");
    }

    /** 验证批量调库下一步校验接口支持调出 */
    @Test
    public void shouldCheckBatchOutboundAdjust() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1001\",\"direction\":\"out\",\"poolId\":2,\"securities\":[{\"securityCode\":\"100001\"}]}");
    }

    /** 验证批量调库提交接口支持调入 */
    @Test
    public void shouldAddBatchInboundAdjustLog() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1001\",\"direction\":\"in\",\"adjusterId\":\"1001\",\"adjusterName\":\"管理员\",\"poolId\":2,\"items\":[{\"securityCode\":\"100001\",\"targetPoolId\":2,\"adjustMode\":\"调入\",\"flowId\":1}]}");
    }

    /** 验证批量调库提交接口支持调出 */
    @Test
    public void shouldAddBatchOutboundAdjustLog() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/batchSecurityPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1001\",\"direction\":\"out\",\"adjusterId\":\"1001\",\"adjusterName\":\"管理员\",\"poolId\":2,\"items\":[{\"securityCode\":\"100001\",\"targetPoolId\":2,\"adjustMode\":\"调出\",\"flowId\":1}]}");
    }
}

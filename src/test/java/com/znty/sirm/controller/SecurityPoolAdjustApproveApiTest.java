package com.znty.sirm.controller;

import com.znty.sirm.service.SecurityPoolAdjustFlowService;
import com.znty.sirm.service.SecurityPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 证券池调库审核页面接口测试
 */
public class SecurityPoolAdjustApproveApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolAdjustController adjustController = new SecurityPoolAdjustController();
        SecurityPoolAdjustFlowController flowController = new SecurityPoolAdjustFlowController();
        ReflectionTestUtils.setField(adjustController, "securityPoolAdjustService", mock(SecurityPoolAdjustService.class));
        ReflectionTestUtils.setField(flowController, "securityPoolAdjustFlowService", mock(SecurityPoolAdjustFlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(adjustController, flowController).build();
    }

    /** 验证 shouldSupportApprovalContextQuery 测试场景。 */
    @Test
    public void shouldSupportApprovalContextQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    /** 验证 shouldSupportApprovalDecision 测试场景。 */
    @Test
    public void shouldSupportApprovalDecision() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjustFlow/submitAdjustAudit",
                "{\"stepId\":1,\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\",\"processAction\":\"approve\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/addAdjustLog", "{}");
    }

    /** 验证驳回修改审批提交支持 multipart 附件变更。 */
    @Test
    public void shouldSupportApprovalDecisionWithFiles() throws Exception {
        MockMultipartFile request = new MockMultipartFile(
                "request", "", "application/json",
                ("{\"stepId\":1,\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\","
                        + "\"processAction\":\"approve\",\"handlerId\":\"1\","
                        + "\"attachmentChanges\":[{\"adjustLogId\":1,\"creditReportFileIndexes\":[0]}]}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "files", "信评报告.pdf", "application/pdf",
                "report".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(fileUpload("/api/v1/securityPoolAdjustFlow/submitAdjustAudit")
                        .file(request)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}

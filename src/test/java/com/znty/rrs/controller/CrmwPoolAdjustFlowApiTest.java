package com.znty.rrs.controller;

import com.znty.rrs.service.CrmwPoolAdjustFlowService;
import com.znty.rrs.service.CrmwPoolAdjustService;
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
 * CRMW池调整审核页面接口测试
 */
public class CrmwPoolAdjustFlowApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        CrmwPoolAdjustController adjustController = new CrmwPoolAdjustController();
        CrmwPoolAdjustFlowController flowController = new CrmwPoolAdjustFlowController();
        ReflectionTestUtils.setField(adjustController, "crmwPoolAdjustService", mock(CrmwPoolAdjustService.class));
        ReflectionTestUtils.setField(flowController, "crmwPoolAdjustFlowService", mock(CrmwPoolAdjustFlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(adjustController, flowController).build();
    }

    /** 验证 shouldSupportApprovalContextQuery 测试场景。 */
    @Test
    public void shouldSupportApprovalContextQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryBindableSecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwDetail", "{\"securityCode\":\"CRMW24001.IB\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/queryCrmwAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    /** 验证 shouldSupportApprovalDecision 测试场景。 */
    @Test
    public void shouldSupportApprovalDecision() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjustFlow/submitAdjustAudit",
                "{\"stepId\":1,\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\",\"processAction\":\"approve\"}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/checkCrmwAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/crmwPoolAdjust/addCrmwAdjustLog", "{}");
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

        mockMvc.perform(fileUpload("/api/v1/crmwPoolAdjustFlow/submitAdjustAuditWithFiles")
                        .file(request)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

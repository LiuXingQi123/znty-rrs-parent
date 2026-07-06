package com.znty.rrs.controller;

import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import com.znty.rrs.service.SysAttachmentService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统附件接口测试
 */
public class SysAttachmentControllerTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 系统附件业务服务 */
    private SysAttachmentService sysAttachmentService;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        SysAttachmentController controller = new SysAttachmentController();
        sysAttachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(controller, "sysAttachmentService", sysAttachmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证下载附件返回 Base64 统一响应 */
    @Test
    public void downloadAttachment_ExistingFile_ReturnsBase64ApiResponse() throws Exception {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        when(sysAttachmentService.downloadAttachment(any(SysAttachmentReq.class)))
                .thenReturn(new SysAttachmentService.DownloadFile("报告附件.pdf", "application/pdf", 5L, content));

        postJson(mockMvc, "/api/v1/attachments/downloadAttachment", "{\"id\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("aGVsbG8="));
    }

    /** 验证查询附件列表仍返回统一响应 */
    @Test
    public void queryAttachmentList_ValidRequest_ReturnsApiResponse() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/attachments/queryAttachmentList", "{\"adjustLogId\":1}");
    }
}

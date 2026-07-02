package com.znty.sirm.controller;

import com.znty.sirm.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.sirm.service.SecurityPoolAdjustService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 证券池调库 multipart 提交接口测试
 */
public class SecurityPoolAdjustMultipartApiTest {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        SecurityPoolAdjustController controller = new SecurityPoolAdjustController();
        SecurityPoolAdjustService service = mock(SecurityPoolAdjustService.class);
        when(service.addAdjustLog(
                any(com.znty.sirm.entity.securitypooladjust.SecurityPoolAdjustSubmitReq.class), any(java.util.List.class)))
                .thenReturn(new AdjustSubmitDto());
        ReflectionTestUtils.setField(controller, "securityPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证业务 JSON 和文件可在同一次请求中提交 */
    @Test
    public void addAdjustLog_MultipartRequest_ReturnsSuccess() throws Exception {
        MockMultipartFile request = new MockMultipartFile(
                "request", "", "application/json",
                ("{\"securityCode\":\"S001\",\"adjusterId\":\"1001\",\"items\":[]}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "files", "信评报告.pdf", "application/pdf",
                "report".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(fileUpload("/api/v1/securityPoolAdjust/addAdjustLogWithFiles")
                        .file(request)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

package com.znty.rrs.controller;

import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.service.FundPoolAdjustService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 基金池调整 multipart 提交接口测试 */
public class FundPoolAdjustMultipartApiTest {
    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        FundPoolAdjustController controller = new FundPoolAdjustController();
        FundPoolAdjustService service = mock(FundPoolAdjustService.class);
        when(service.addAdjustLog(any(FundPoolAdjustSubmitReq.class), any(List.class), anyString()))
                .thenReturn(new FundAdjustSubmitDto());
        ReflectionTestUtils.setField(controller, "fundPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证业务 JSON、文件和原始文件名可以同次提交 */
    @Test
    public void addAdjustLogWithFilesReturnsSuccess() throws Exception {
        MockMultipartFile request = new MockMultipartFile(
                "request", "", "application/json",
                ("{\"fundCode\":\"FUND001.SH\",\"fundScore\":8.5,"
                        + "\"fundInvestmentType\":\"stock\",\"adjusterId\":\"1\",\"items\":[]}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "files", "基金报告.pdf", "application/pdf", "report".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/fundPoolAdjust/addAdjustLogWithFiles")
                        .file(request)
                        .file(file)
                        .param("originalFileNameListJson", "[\"基金报告.pdf\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

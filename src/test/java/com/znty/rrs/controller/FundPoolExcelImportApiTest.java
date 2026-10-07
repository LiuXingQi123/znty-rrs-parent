package com.znty.rrs.controller;

import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.service.FundPoolExcelImportService;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 基金 Excel 导入六个接口及 multipart 原始中文文件名契约测试。 */
public class FundPoolExcelImportApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端。 */
    private MockMvc mockMvc;
    /** 模拟业务服务。 */
    private FundPoolExcelImportService service;

    /** 构造独立接口测试客户端。 */
    @Before
    public void setUp() {
        service = mock(FundPoolExcelImportService.class);
        FundPoolExcelImportController controller = new FundPoolExcelImportController();
        ReflectionTestUtils.setField(controller, "fundPoolExcelImportService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** JSON 查询、校验、提交和取消均使用统一成功响应。 */
    @Test
    public void jsonEndpointsShouldUseStandardResponse() throws Exception {
        for (String method : new String[] {"queryTask", "queryItemPage", "checkImport", "submitImport", "cancelImport"}) {
            assertPostSuccess(mockMvc, "/api/v1/fundPoolExcelImport/" + method, "{\"impId\":\"IMPORT\",\"currentUserId\":\"1\"}");
        }
    }

    /** multipart JSON、文件和原始文件名文本字段按证券接口口径传递。 */
    @Test
    public void uploadShouldBindJsonAndOriginalChineseFileName() throws Exception {
        when(service.uploadExcel(any(FundPoolExcelImportReq.class), any(MultipartFile.class), anyString()))
                .thenReturn(new FundPoolExcelImportDto());
        MockMultipartFile request = new MockMultipartFile("request", "", "application/json",
                "{\"direction\":\"in\",\"currentUserId\":\"1\",\"clearTarget\":true,\"clearFundScore\":8.5,\"clearFundInvestmentType\":\"stock\",\"clearNeedRiskLeaderApproval\":1}"
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("file", "encoded.xlsx", "application/octet-stream", new byte[] {1});
        mockMvc.perform(multipart("/api/v1/fundPoolExcelImport/uploadExcel").file(request).file(file)
                        .param("originalFileNameListJson", "[\"基金池导入.xlsx\"]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(service).uploadExcel(any(FundPoolExcelImportReq.class), any(MultipartFile.class), names.capture());
        assertEquals("[\"基金池导入.xlsx\"]", names.getValue());
    }
}

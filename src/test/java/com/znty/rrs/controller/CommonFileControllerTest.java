package com.znty.rrs.controller;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.commonfile.CommonFileReq;
import com.znty.rrs.service.CommonFileService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公共文件接口测试
 */
public class CommonFileControllerTest extends ControllerApiTestSupport {

    private MockMvc mockMvc;
    private CommonFileService commonFileService;

    @Before
    public void setUp() {
        CommonFileController controller = new CommonFileController();
        commonFileService = mock(CommonFileService.class);
        ReflectionTestUtils.setField(controller, "commonFileService", commonFileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void downloadTemplate_ValidCode_ReturnsBase64() throws Exception {
        CommonFileDto dto = new CommonFileDto();
        dto.setFileName("security_pool_import.xlsx");
        dto.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        dto.setFileSize(10L);
        dto.setContentBase64("aGVsbG8=");
        when(commonFileService.downloadTemplate(any(CommonFileReq.class))).thenReturn(dto);

        postJson(mockMvc, "/api/v1/commonFile/downloadTemplate", "{\"templateCode\":\"security_pool_import\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("security_pool_import.xlsx"))
                .andExpect(jsonPath("$.data.contentBase64").value("aGVsbG8="));
    }
}

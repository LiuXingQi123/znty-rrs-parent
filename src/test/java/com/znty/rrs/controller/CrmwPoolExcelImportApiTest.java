package com.znty.rrs.controller;

import com.znty.rrs.service.CrmwPoolExcelImportService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * CRMW 池 Excel 导入接口测试
 */
public class CrmwPoolExcelImportApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        CrmwPoolExcelImportController controller = new CrmwPoolExcelImportController();
        ReflectionTestUtils.setField(
                controller,
                "crmwPoolExcelImportService",
                mock(CrmwPoolExcelImportService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证可导入 CRMW 池列表接口 */
    @Test
    public void shouldQueryPoolList() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/queryPoolList",
                "{\"currentUserId\":\"1\"}");
    }

    /** 验证查询导入批次接口 */
    @Test
    public void shouldQueryTask() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/queryTask",
                "{\"impId\":\"IMP1\"}");
    }

    /** 验证明细分页接口 */
    @Test
    public void shouldQueryItemPage() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/queryItemPage",
                "{\"impId\":\"IMP1\",\"pageIndex\":1,\"pageSize\":20}");
    }

    /** 验证导入校验接口 */
    @Test
    public void shouldCheckImport() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/checkImport",
                "{\"impId\":\"IMP1\",\"currentUserId\":\"1\"}");
    }

    /** 验证导入提交接口 */
    @Test
    public void shouldSubmitImport() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/submitImport",
                "{\"impId\":\"IMP1\",\"currentUserId\":\"1\",\"currentUserName\":\"管理员\"}");
    }

    /** 验证取消导入接口 */
    @Test
    public void shouldCancelImport() throws Exception {
        assertPostSuccess(
                mockMvc,
                "/api/v1/crmwPoolExcelImport/cancelImport",
                "{\"impId\":\"IMP1\"}");
    }
}

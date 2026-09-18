package com.znty.rrs.controller;

import com.znty.rrs.service.CompanyPoolAdjustHistoryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 主体池调整历史页面接口测试
 */
public class CompanyPoolAdjustHistoryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        CompanyPoolAdjustHistoryController controller = new CompanyPoolAdjustHistoryController();
        ReflectionTestUtils.setField(controller, "companyPoolAdjustHistoryService",
                mock(CompanyPoolAdjustHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证主体池调整历史查询与导出接口。 */
    @Test
    public void shouldSupportCompanyPoolAdjustHistoryQueryAndExport() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/companyPoolAdjustHistory/queryCompanyPoolAdjustHistoryPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/companyPoolAdjustHistory/exportCompanyPoolAdjustHistoryExcel",
                "{\"currentUserId\":\"1\",\"companyCode\":\"C10001\"}");
    }
}

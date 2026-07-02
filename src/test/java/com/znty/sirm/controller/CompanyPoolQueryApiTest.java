package com.znty.sirm.controller;

import com.znty.sirm.service.CompanyPoolQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 主体池查询页面接口测试
 */
public class CompanyPoolQueryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        CompanyPoolQueryController controller = new CompanyPoolQueryController();
        ReflectionTestUtils.setField(controller, "companyPoolQueryService", mock(CompanyPoolQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportCompanyPoolQuery 测试场景。 */
    @Test
    public void shouldSupportCompanyPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/companyPoolQuery/queryCompanyPoolPage", "{}");
    }
}

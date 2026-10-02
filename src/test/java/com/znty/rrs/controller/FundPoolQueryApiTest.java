package com.znty.rrs.controller;

import com.znty.rrs.service.FundPoolQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/** 基金池查询页面接口测试。 */
public class FundPoolQueryApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        FundPoolQueryController controller = new FundPoolQueryController();
        ReflectionTestUtils.setField(controller, "fundPoolQueryService", mock(FundPoolQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证基金池查询、类型选项及导出接口。 */
    @Test
    public void shouldSupportFundPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/fundPoolQuery/queryFundPoolPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolQuery/queryFundPoolPage",
                "{\"poolIds\":[2],\"fundCode\":\"FUND001\",\"securityType\":\"etf_fund\",\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolQuery/queryFundTypeList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolQuery/exportFundPoolExcel", "{\"fundName\":\"测试基金\"}");
    }
}

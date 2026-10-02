package com.znty.rrs.controller;

import com.znty.rrs.service.FundPoolAdjustHistoryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/** 基金池调整历史页面接口测试。 */
public class FundPoolAdjustHistoryApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        // 构建独立的基金池调整历史接口测试客户端
        FundPoolAdjustHistoryController controller = new FundPoolAdjustHistoryController();
        ReflectionTestUtils.setField(controller, "fundPoolAdjustHistoryService",
                mock(FundPoolAdjustHistoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证基金池调整历史页面所需接口。 */
    @Test
    public void shouldSupportFundPoolAdjustHistoryQueryAndExport() throws Exception {
        // 覆盖无条件查询、条件查询、筛选选项和导出接口
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjustHistory/queryFundPoolAdjustHistoryPage", "{}");
        assertPostSuccess(
                mockMvc,
                "/api/v1/fundPoolAdjustHistory/queryFundPoolAdjustHistoryPage",
                "{\"poolIds\":[145,146],\"fundName\":\"测试基金\",\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjustHistory/queryFundTypeList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/fundPoolAdjustHistory/exportFundPoolAdjustHistoryExcel",
                "{\"fundCode\":\"FUND001\"}");
    }
}

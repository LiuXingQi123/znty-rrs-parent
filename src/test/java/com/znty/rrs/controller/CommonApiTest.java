package com.znty.rrs.controller;

import com.znty.rrs.service.CommonService;
import com.znty.rrs.common.enums.MarketCode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 公共查询接口测试
 */
public class CommonApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 公共查询服务桩 */
    private CommonService commonService;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        CommonController controller = new CommonController();
        commonService = mock(CommonService.class);
        ReflectionTestUtils.setField(controller, "commonService", commonService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 共用市场接口仅返回代码，页面自行映射名称。 */
    @Test
    public void shouldQuerySharedMarketCodes() throws Exception {
        when(commonService.queryMarketCodeList()).thenReturn(MarketCode.queryCodeList());
        postJson(mockMvc, "/api/v1/common/queryMarketCodeList", "{}")
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(MarketCode.values().length))
                .andExpect(jsonPath("$.data[0]").value("SSE"));
    }

    /** 验证公共投资池树列表接口 */
    @Test
    public void shouldQueryCommonPoolTreeList() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/common/queryPoolTreeList", "{}");
        assertPostSuccess(
                mockMvc,
                "/api/v1/common/queryPoolTreeList",
                "{\"excludePoolTypes\":[\"crmw\",\"forbidden\"]}");
    }

    /** 验证批量查询担保人主体内评分接口 */
    @Test
    public void shouldQueryGuarantorGradeList() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/common/queryGuarantorGradeList",
                "{\"securityCodes\":[\"DBB001.IB\",\"DBB002.IB\"]}");
    }
}

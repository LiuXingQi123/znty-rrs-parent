package com.znty.rrs.controller;

import com.znty.rrs.service.CommonService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 公共查询接口测试
 */
public class CommonApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        CommonController controller = new CommonController();
        ReflectionTestUtils.setField(controller, "commonService", mock(CommonService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}

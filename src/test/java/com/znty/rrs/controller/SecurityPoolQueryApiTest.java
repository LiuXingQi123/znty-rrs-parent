package com.znty.rrs.controller;

import com.znty.rrs.service.SecurityPoolQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 证券池查询页面接口测试
 */
public class SecurityPoolQueryApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolQueryController controller = new SecurityPoolQueryController();
        ReflectionTestUtils.setField(controller, "securityPoolQueryService", mock(SecurityPoolQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportSecurityPoolQuery 测试场景。 */
    @Test
    public void shouldSupportSecurityPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityPoolPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityTypeList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityStatusList", "{}");
    }

    /** 验证 shouldSupportMyPoolFavorites 测试场景。 */
    @Test
    public void shouldSupportMyPoolFavorites() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/addSecurityToMyPool", "{\"userId\":\"1\",\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/deleteSecurityFromMyPool", "{\"userId\":\"1\",\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/queryFavoritedCodeList", "{\"userId\":\"1\"}");
    }
}

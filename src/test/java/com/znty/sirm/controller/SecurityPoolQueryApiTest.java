package com.znty.sirm.controller;

import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.service.SecurityPoolQueryService;
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

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        SecurityPoolQueryController controller = new SecurityPoolQueryController();
        ReflectionTestUtils.setField(controller, "securityPoolQueryService", mock(SecurityPoolQueryService.class));
        ReflectionTestUtils.setField(controller, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldSupportSecurityPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityPoolPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityTypeList");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/querySecurityStatusList");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/queryPoolTreeList");
    }

    @Test
    public void shouldSupportMyPoolFavorites() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/addToMyPool", "{\"userId\":\"1001\",\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/deleteFromMyPool", "{\"userId\":\"1001\",\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolQuery/queryFavoritedCodeList", "{\"userId\":\"1001\"}");
    }
}

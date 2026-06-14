package com.znty.sirm.controller;

import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.service.SubjectPoolQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 主体池查询页面接口测试
 */
public class SubjectPoolQueryApiTest extends ControllerApiTestSupport {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        SubjectPoolQueryController controller = new SubjectPoolQueryController();
        ReflectionTestUtils.setField(controller, "subjectPoolQueryService", mock(SubjectPoolQueryService.class));
        ReflectionTestUtils.setField(controller, "investmentPoolMapper", mock(InvestmentPoolMapper.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldSupportSubjectPoolQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/subjectPoolQuery/querySubjectPoolPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/subjectPoolQuery/queryPoolTreeList");
    }
}

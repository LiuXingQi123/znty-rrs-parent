package com.znty.sirm.controller;

import com.znty.sirm.service.RuleService;
import com.znty.sirm.service.TestCaseService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 规则管理中心页面接口测试
 */
public class RuleManagerApiTest extends ControllerApiTestSupport {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        RuleController ruleController = new RuleController();
        TestCaseController testCaseController = new TestCaseController();
        ReflectionTestUtils.setField(ruleController, "ruleService", mock(RuleService.class));
        ReflectionTestUtils.setField(testCaseController, "testCaseService", mock(TestCaseService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController, testCaseController).build();
    }

    @Test
    public void shouldSupportRuleLifecycle() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/rules/queryRulePage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/rules/queryRuleDetail", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/rules/addOrEditRule", "{}");
        assertPostSuccess(mockMvc, "/api/v1/rules/editRuleStatus", "{\"id\":1,\"status\":\"active\"}");
        assertPostSuccess(mockMvc, "/api/v1/rules/deleteRule", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/rules/rule-runs/executeRule", "{\"id\":1}");
    }

    @Test
    public void shouldSupportRuleOptions() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/rules/options/queryCategoryList");
        assertPostSuccess(mockMvc, "/api/v1/rules/options/queryPresetSetList");
    }

    @Test
    public void shouldSupportTestCaseRegressionFlow() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/testCases/queryTestCasePage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/testCases/addOrEditTestCase", "{}");
        assertPostSuccess(mockMvc, "/api/v1/testCases/editTestCaseName", "{\"id\":1,\"name\":\"回归用例\"}");
        assertPostSuccess(mockMvc, "/api/v1/testCases/deleteTestCase", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/testCases/runTestCase", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/testCases/runAllTestCases");
        assertPostSuccess(mockMvc, "/api/v1/testCases/queryRunHistoryList", "{\"id\":1}");
    }
}

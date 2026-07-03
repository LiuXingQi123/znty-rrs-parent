package com.znty.rrs.controller;

import com.znty.rrs.service.InvestmentPoolService;
import com.znty.rrs.service.FlowService;
import com.znty.rrs.service.RuleService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 投资池维护页面接口测试
 */
public class InvestmentPoolApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        InvestmentPoolController poolController = new InvestmentPoolController();
        FlowController flowController = new FlowController();
        RuleController ruleController = new RuleController();
        ReflectionTestUtils.setField(poolController, "investmentPoolService", mock(InvestmentPoolService.class));
        ReflectionTestUtils.setField(flowController, "flowService", mock(FlowService.class));
        ReflectionTestUtils.setField(ruleController, "ruleService", mock(RuleService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(poolController, flowController, ruleController).build();
    }

    /** 验证 shouldSupportPoolTreeMaintenance 测试场景。 */
    @Test
    public void shouldSupportPoolTreeMaintenance() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/queryPoolList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/queryPoolDetail", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/addRootPool", "{}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/addChildPool", "{\"parentId\":1}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/deletePoolNode", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/addSeedPoolList", "{}");
    }

    /** 验证 shouldSupportPoolConfiguration 测试场景。 */
    @Test
    public void shouldSupportPoolConfiguration() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/editPoolConfig", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/editPoolRelation", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/editPoolPermission", "{\"id\":1}");
    }

    /** 验证 shouldSupportPoolConfigurationOptions 测试场景。 */
    @Test
    public void shouldSupportPoolConfigurationOptions() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/queryFlowOptionList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/queryRoleList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/investmentPool/queryUserList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowList", "{\"status\":\"active\"}");
        assertPostSuccess(mockMvc, "/api/v1/rules/options/queryCategoryList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/rules/queryRulePage", "{\"status\":\"active\"}");
    }
}

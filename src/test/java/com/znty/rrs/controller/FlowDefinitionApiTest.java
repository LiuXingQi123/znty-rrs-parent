package com.znty.rrs.controller;

import com.znty.rrs.service.FlowService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 流程定义页面接口测试
 */
public class FlowDefinitionApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        FlowController controller = new FlowController();
        ReflectionTestUtils.setField(controller, "flowService", mock(FlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportFlowDefinitionCrud 测试场景。 */
    @Test
    public void shouldSupportFlowDefinitionCrud() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/addFlow", "{\"operatorId\":8}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowDetail", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlow", "{\"id\":1,\"operatorId\":8}");
        assertPostSuccess(mockMvc, "/api/v1/flows/deleteFlow", "{\"id\":1,\"operatorId\":8}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowStatus", "{\"id\":1,\"operatorId\":8}");
    }

    /** 验证 shouldSupportDesignerDraftAndPublish 测试场景。 */
    @Test
    public void shouldSupportDesignerDraftAndPublish() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowDraft", "{\"operatorId\":12}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowToPublished", "{\"operatorId\":15}");
    }

    /** 验证 shouldSupportVersionHistory 测试场景。 */
    @Test
    public void shouldSupportVersionHistory() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowVersionList", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowVersionDetail", "{\"flowId\":1,\"versionId\":1}");
    }

    /** 验证 shouldSupportDesignerDictionaries 测试场景。 */
    @Test
    public void shouldSupportDesignerDictionaries() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryRoleList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryUserList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryAutoTaskList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryCondFieldList", "{}");
    }
}

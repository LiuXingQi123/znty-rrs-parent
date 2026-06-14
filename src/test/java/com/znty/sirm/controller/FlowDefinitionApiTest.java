package com.znty.sirm.controller;

import com.znty.sirm.service.FlowService;
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

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        FlowController controller = new FlowController();
        ReflectionTestUtils.setField(controller, "flowService", mock(FlowService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldSupportFlowDefinitionCrud() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/addFlow", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowDetail", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlow", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/deleteFlow", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowStatus", "{\"id\":1}");
    }

    @Test
    public void shouldSupportDesignerDraftAndPublish() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowDraft", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/editFlowToPublished", "{}");
    }

    @Test
    public void shouldSupportVersionHistory() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowVersionList", "{\"id\":1}");
        assertPostSuccess(mockMvc, "/api/v1/flows/queryFlowVersionDetail", "{\"flowId\":1,\"versionId\":1}");
    }

    @Test
    public void shouldSupportDesignerDictionaries() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryRoleList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryUserList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryAutoTaskList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/flows/dict/queryCondFieldList", "{}");
    }
}

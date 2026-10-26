package com.znty.rrs.controller;

import com.znty.rrs.service.SecurityPoolAdjustService;
import com.znty.rrs.entity.securitypooladjust.RedirectPoolOptionDto;
import org.springframework.http.MediaType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 证券池调库申请页面接口测试
 */
public class SecurityPoolAdjustApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 调库业务服务 */
    private SecurityPoolAdjustService service;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolAdjustController controller = new SecurityPoolAdjustController();
        service = mock(SecurityPoolAdjustService.class);
        ReflectionTestUtils.setField(controller, "securityPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportSecuritySelectionAndDetail 测试场景。 */
    @Test
    public void shouldSupportSecuritySelectionAndDetail() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityTypeList", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryIssuerFinancialList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryIssuerFinancialByReportDate",
                "{\"securityCode\":\"100001\",\"reportDate\":20251231}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/saveIssuerFinancialList",
                "{\"securityCode\":\"100001\",\"records\":[{\"reportDate\":20251231,\"totAssets\":\"32.480712\"}]}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryRelatedRatingCompanyList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySelfSelectedRightsHolderPage", "{\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPoolStatus", "{\"securityCode\":\"100001\"}");
    }

    /** 验证 shouldSupportAdjustValidationAndSubmission 测试场景。 */
    @Test
    public void shouldSupportAdjustValidationAndSubmission() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/addAdjustLog", "{}");
    }

    /** 验证 shouldSupportAdjustTrace 测试场景。 */
    @Test
    public void shouldSupportAdjustTrace() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    /** 候选池接口必须转交本笔申请 ID，不能仅按证券主档查询 */
    @Test
    public void shouldForwardApplicationContextForRedirectOptions() throws Exception {
        RedirectPoolOptionDto option = new RedirectPoolOptionDto();
        option.setId(2L);
        option.setPoolName("一级库");
        option.setPoolFullName("信用债大库(new)/一级库");
        when(service.queryRedirectPoolOptions("ABS.SH", 101L)).thenReturn(Collections.singletonList(option));
        mockMvc.perform(post("/api/v1/securityPoolAdjust/queryRedirectPoolOptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"securityCode\":\"ABS.SH\",\"adjustLogId\":101}"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].poolName").value("一级库"))
                .andExpect(jsonPath("$.data[0].poolFullName").value("信用债大库(new)/一级库"));
        verify(service).queryRedirectPoolOptions("ABS.SH", 101L);
    }
}

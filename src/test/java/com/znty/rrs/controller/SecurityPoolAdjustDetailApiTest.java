package com.znty.rrs.controller;

import com.znty.rrs.service.SecurityPoolAdjustService;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 证券池调库详情页面接口测试
 */
public class SecurityPoolAdjustDetailApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 调库服务桩，用于验证主档值原样返回。 */
    private SecurityPoolAdjustService service;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        SecurityPoolAdjustController controller = new SecurityPoolAdjustController();
        service = mock(SecurityPoolAdjustService.class);
        ReflectionTestUtils.setField(controller, "securityPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证 shouldSupportCompleteAdjustDetailLoading 测试场景。 */
    @Test
    public void shouldSupportCompleteAdjustDetailLoading() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPage", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityDetail", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustPoolList", "{\"securityCode\":\"100001\",\"adjustDirection\":\"in\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/querySecurityPoolStatus", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustLogList", "{\"securityCode\":\"100001\"}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/queryAdjustStepList", "{\"adjustLogId\":1,\"adjustBatchNo\":\"BATCH001\"}");
    }

    /** 验证 shouldSupportDetailPageValidationAndResubmission 测试场景。 */
    @Test
    public void shouldSupportDetailPageValidationAndResubmission() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/checkAdjust", "{}");
        assertPostSuccess(mockMvc, "/api/v1/securityPoolAdjust/addAdjustLog", "{}");
    }

    /** 对比接口返回当前主档值，保持统一响应和证券代码。 */
    @Test
    public void shouldReturnMasterValuesForComparison() throws Exception {
        SecurityInfoDetailDto master = new SecurityInfoDetailDto();
        master.setWindCode("100001");
        master.setMaturityDate("20290318");
        master.setRatingBondissuer("AAA");
        when(service.querySecurityMasterDetail(any(SecurityPoolAdjustReq.class))).thenReturn(master);
        postJson(mockMvc, "/api/v1/securityPoolAdjust/querySecurityMasterDetail", "{\"securityCode\":\"100001\"}")
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.windCode").value("100001"))
                .andExpect(jsonPath("$.data.maturityDate").value("20290318"))
                .andExpect(jsonPath("$.data.ratingBondissuer").value("AAA"));
    }
}

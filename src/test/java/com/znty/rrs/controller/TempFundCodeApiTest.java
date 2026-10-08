package com.znty.rrs.controller;

import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.exception.ExceptionConfig;
import com.znty.rrs.service.TempFundCodeService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 基金临时代码接口测试，覆盖七个 POST、反序列化和统一错误响应。 */
public class TempFundCodeApiTest extends ControllerApiTestSupport {
    /** API 测试客户端 */
    private MockMvc mockMvc;
    /** 人工服务替身 */
    private TempFundCodeService service;
    /** 全部已实现接口方法 */
    private static final String[] METHODS = {"queryTempFundCodePage", "queryTempFundCodeOptions",
            "queryFormalFundOptionList", "addTempFundCode", "editTempFundCodeToUpdated",
            "editTempFundCodeToCancelled", "deleteTempFundCode"};

    /** 挂载真实 Controller 和项目异常响应处理器。 */
    @Before
    public void setUp() {
        service = mock(TempFundCodeService.class);
        TempFundCodeController controller = new TempFundCodeController();
        ReflectionTestUtils.setField(controller, "tempFundCodeService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExceptionConfig()).build();
    }

    /** 七个接口都可通过 JSON POST 访问。 */
    @Test
    public void shouldExposeAllSevenPostEndpoints() throws Exception {
        for (String method : METHODS) {
            assertPostSuccess(mockMvc, "/api/v1/tempFundCode/" + method, "{}");
        }
        verify(service).queryTempFundCodePage(any(TempFundCodeReq.class));
        verify(service).queryTempFundCodeOptions(any(TempFundCodeReq.class));
        verify(service).queryFormalFundOptionList(any(TempFundCodeReq.class));
        verify(service).addTempFundCode(any(TempFundCodeReq.class));
        verify(service).editTempFundCodeToUpdated(any(TempFundCodeReq.class));
        verify(service).editTempFundCodeToCancelled(any(TempFundCodeReq.class));
        verify(service).deleteTempFundCode(any(TempFundCodeReq.class));
    }

    /** GET 及缺少请求体都拒绝，业务服务不执行。 */
    @Test
    public void shouldRejectGetAndMissingBodies() throws Exception {
        for (String method : METHODS) {
            mockMvc.perform(get("/api/v1/tempFundCode/" + method))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("请求方法不支持")));
            mockMvc.perform(post("/api/v1/tempFundCode/" + method))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("请求体解析失败")));
        }
        verifyNoInteractions(service);
    }

    /** 列表筛选及分页字段准确绑定，返回标准分页记录。 */
    @Test
    public void shouldBindFiltersAndSerializePage() throws Exception {
        TempFundCodeDto row = new TempFundCodeDto();
        row.setId(1L);
        row.setTempFundCode("TMPF001");
        when(service.queryTempFundCodePage(any(TempFundCodeReq.class)))
                .thenReturn(new PageResult<>(Collections.singletonList(row), 1L, 2, 10));
        postJson(mockMvc, "/api/v1/tempFundCode/queryTempFundCodePage",
                "{\"tempFundCode\":\"TMPF\",\"tempFundShortName\":\"基金\","
                        + "\"statusList\":[\"temporary\",\"cancelled\"],\"oprtSourceList\":[\"manual\"],"
                        + "\"pageIndex\":2,\"pageSize\":10}")
                .andExpect(jsonPath("$.data.records[0].tempFundCode").value("TMPF001"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.pageIndex").value(2));
        ArgumentCaptor<TempFundCodeReq> request = ArgumentCaptor.forClass(TempFundCodeReq.class);
        verify(service).queryTempFundCodePage(request.capture());
        assertThat(request.getValue().getStatusList()).isEqualTo(Arrays.asList("temporary", "cancelled"));
        assertThat(request.getValue().getOprtSourceList()).containsExactly("manual");
        assertThat(request.getValue().getTempFundShortName()).isEqualTo("基金");
        assertThat(request.getValue().getPageSize()).isEqualTo(10);
    }

    /** 新增四项及登录操作人准确传递。 */
    @Test
    public void shouldBindFourAddFieldsAndOperator() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/tempFundCode/addTempFundCode",
                "{\"tempFundCode\":\"TMPF001\",\"tempFundShortName\":\"临时基金\","
                        + "\"tempMarketCode\":\"SSE\",\"tempSecurityType\":\"etf_fund\",\"operatorId\":\"17\"}");
        ArgumentCaptor<TempFundCodeReq> request = ArgumentCaptor.forClass(TempFundCodeReq.class);
        verify(service).addTempFundCode(request.capture());
        assertThat(request.getValue().getTempFundCode()).isEqualTo("TMPF001");
        assertThat(request.getValue().getTempFundShortName()).isEqualTo("临时基金");
        assertThat(request.getValue().getTempMarketCode()).isEqualTo("SSE");
        assertThat(request.getValue().getTempSecurityType()).isEqualTo("etf_fund");
        assertThat(request.getValue().getOperatorId()).isEqualTo("17");
    }

    /** 正式快照没有请求字段入口，额外假字段不覆盖权威结果。 */
    @Test
    public void shouldIgnoreUntrustedFormalSnapshotFields() throws Exception {
        TempFundCodeDto stored = new TempFundCodeDto();
        stored.setFundCode("FUND001");
        stored.setFundName("权威正式全称");
        stored.setSecurityType("lof_fund");
        when(service.editTempFundCodeToUpdated(any(TempFundCodeReq.class))).thenReturn(stored);
        postJson(mockMvc, "/api/v1/tempFundCode/editTempFundCodeToUpdated",
                "{\"id\":1,\"fundCode\":\"FUND001\",\"operatorId\":\"17\","
                        + "\"fundName\":\"伪造\",\"fundShortName\":\"伪造\",\"marketCode\":\"BAD\",\"securityType\":\"mtn\"}")
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fundName").value("权威正式全称"))
                .andExpect(jsonPath("$.data.securityType").value("lof_fund"));
        ArgumentCaptor<TempFundCodeReq> request = ArgumentCaptor.forClass(TempFundCodeReq.class);
        verify(service).editTempFundCodeToUpdated(request.capture());
        assertThat(request.getValue().getFundCode()).isEqualTo("FUND001");
        assertThat(request.getValue().getOperatorId()).isEqualTo("17");
    }

    /** 业务异常返回明确文案，不在响应体输出状态码。 */
    @Test
    public void shouldReturnBusinessFailureWithoutExposingStatusCode() throws Exception {
        when(service.deleteTempFundCode(any(TempFundCodeReq.class)))
                .thenThrow(new BizException("临时基金已被调库业务使用，无法删除"));
        postJson(mockMvc, "/api/v1/tempFundCode/deleteTempFundCode", "{\"id\":1,\"operatorId\":\"17\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("临时基金已被调库业务使用，无法删除"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}

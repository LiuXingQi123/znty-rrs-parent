package com.znty.rrs.controller;

import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustDto;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustReq;
import com.znty.rrs.exception.ExceptionConfig;
import com.znty.rrs.service.BatchFundPoolAdjustService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 基金池批量调整五个 POST 接口与 multipart 中文文件名契约测试 */
public class BatchFundPoolAdjustApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端 */
    private MockMvc mockMvc;
    /** 批量调整服务 */
    private BatchFundPoolAdjustService service;

    /** 初始化控制器和统一异常处理。 */
    @Before
    public void setUp() {
        BatchFundPoolAdjustController controller = new BatchFundPoolAdjustController();
        service = mock(BatchFundPoolAdjustService.class);
        ReflectionTestUtils.setField(controller, "batchFundPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExceptionConfig()).build();
    }

    /** 目标池分页及基金候选分页沿用统一响应。 */
    @Test
    public void shouldSupportPoolAndFundPages() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/batchFundPoolAdjust/queryPoolPage",
                "{\"currentUserId\":\"1\",\"poolIds\":[10],\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/batchFundPoolAdjust/queryFundPage",
                "{\"currentUserId\":\"1\",\"poolId\":10,\"direction\":\"out\",\"fundCode\":\"FUND\"}");
    }

    /** 校验请求正确读取基金数组及调整方向。 */
    @Test
    public void shouldReadBatchFundCheckContract() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/batchFundPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1\",\"poolId\":10,\"direction\":\"in\",\"funds\":[{\"fundCode\":\"FUND001.SH\"}]}");
        ArgumentCaptor<BatchFundAdjustReq> request = ArgumentCaptor.forClass(BatchFundAdjustReq.class);
        verify(service).checkAdjust(request.capture());
        assertThat(request.getValue().getFunds().get(0).getFundCode()).isEqualTo("FUND001.SH");
    }

    /** JSON 提交保留零分和领导审批为否。 */
    @Test
    public void shouldReadSharedFieldsAndJsonSubmitResult() throws Exception {
        BatchFundAdjustDto dto = new BatchFundAdjustDto();
        dto.setFundCount(2);
        dto.setSubmitCount(3);
        when(service.addAdjustLog(any())).thenReturn(dto);
        postJson(mockMvc, "/api/v1/batchFundPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1\",\"fundScore\":0,\"fundInvestmentType\":\"stock\",\"needRiskLeaderApproval\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fundCount").value(2))
                .andExpect(jsonPath("$.data.submitCount").value(3));
        ArgumentCaptor<BatchFundAdjustReq> request = ArgumentCaptor.forClass(BatchFundAdjustReq.class);
        verify(service).addAdjustLog(request.capture());
        assertThat(request.getValue().getFundScore()).isZero();
        assertThat(request.getValue().getNeedRiskLeaderApproval()).isZero();
    }

    /** multipart 同次接收业务 JSON、文件及原始中文文件名。 */
    @Test
    public void shouldSubmitMultipartWithOriginalChineseFileNames() throws Exception {
        when(service.addAdjustLog(any(), anyList(), eq("[\"基金报告.pdf\"]"))).thenReturn(new BatchFundAdjustDto());
        MockMultipartFile request = new MockMultipartFile("request", "", "application/json",
                "{\"fundScore\":0,\"fundInvestmentType\":\"stock\",\"needRiskLeaderApproval\":0}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "基金报告.pdf", "application/pdf", new byte[] {1});
        mockMvc.perform(multipart("/api/v1/batchFundPoolAdjust/addAdjustLogWithFiles")
                        .file(request).file(file).param("originalFileNameListJson", "[\"基金报告.pdf\"]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        ArgumentCaptor<List> files = ArgumentCaptor.forClass(List.class);
        verify(service).addAdjustLog(any(), files.capture(), eq("[\"基金报告.pdf\"]"));
        assertThat(files.getValue()).hasSize(1);
    }
}

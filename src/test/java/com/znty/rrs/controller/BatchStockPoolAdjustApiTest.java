package com.znty.rrs.controller;

import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustDto;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.exception.ExceptionConfig;
import com.znty.rrs.service.BatchStockPoolAdjustService;
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

/** 股票池批量调整五个 POST 接口及 multipart 中文原名契约测试 */
public class BatchStockPoolAdjustApiTest extends ControllerApiTestSupport {
    /** 接口测试客户端 */
    private MockMvc mockMvc;
    /** 批量调整服务 */
    private BatchStockPoolAdjustService service;

    /** 初始化控制器与统一异常处理。 */
    @Before
    public void setUp() {
        BatchStockPoolAdjustController controller = new BatchStockPoolAdjustController();
        service = mock(BatchStockPoolAdjustService.class);
        ReflectionTestUtils.setField(controller, "batchStockPoolAdjustService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExceptionConfig()).build();
    }

    /** 目标池和股票分页保留统一响应及股票专属筛选字段。 */
    @Test
    public void shouldSupportPoolAndStockPages() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/batchStockPoolAdjust/queryPoolPage",
                "{\"currentUserId\":\"1\",\"poolIds\":[10],\"pageIndex\":1,\"pageSize\":20}");
        assertPostSuccess(mockMvc, "/api/v1/batchStockPoolAdjust/queryStockPage",
                "{\"currentUserId\":\"1\",\"poolId\":10,\"direction\":\"out\",\"stockCode\":\"600\",\"stockName\":\"股票\",\"industryCode\":\"A02\",\"marketCode\":\"SSE\",\"securityType\":\"stock_a\"}");
        ArgumentCaptor<BatchStockPoolAdjustReq> request = ArgumentCaptor.forClass(BatchStockPoolAdjustReq.class);
        verify(service).queryStockPage(request.capture());
        assertThat(request.getValue().getStockName()).isEqualTo("股票");
        assertThat(request.getValue().getIndustryCode()).isEqualTo("A02");
    }

    /** 批量校验请求读取股票数组及公开调整方向。 */
    @Test
    public void shouldReadBatchStockCheckContract() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/batchStockPoolAdjust/checkAdjust",
                "{\"currentUserId\":\"1\",\"poolId\":10,\"direction\":\"in\",\"stocks\":[{\"stockCode\":\"600001.SH\"}]}");
        ArgumentCaptor<BatchStockAdjustReq> request = ArgumentCaptor.forClass(BatchStockAdjustReq.class);
        verify(service).checkAdjust(request.capture());
        assertThat(request.getValue().getStocks().get(0).getStockCode()).isEqualTo("600001.SH");
    }

    /** JSON 提交保留完整明细、报告归属和提交统计。 */
    @Test
    public void shouldReadCompleteItemsAndSubmitResult() throws Exception {
        BatchStockAdjustDto dto = new BatchStockAdjustDto();
        dto.setStockCount(2);
        dto.setSubmitCount(3);
        when(service.addAdjustLog(any())).thenReturn(dto);
        postJson(mockMvc, "/api/v1/batchStockPoolAdjust/addAdjustLog",
                "{\"currentUserId\":\"1\",\"poolId\":10,\"direction\":\"in\",\"items\":[{\"stockCode\":\"600001.SH\",\"adjustMode\":\"调入\",\"itemTag\":\"manual\",\"flowType\":\"normalInbound\",\"reportSourceAttachmentIds\":[9]}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockCount").value(2)).andExpect(jsonPath("$.data.submitCount").value(3));
        ArgumentCaptor<BatchStockAdjustReq> request = ArgumentCaptor.forClass(BatchStockAdjustReq.class);
        verify(service).addAdjustLog(request.capture());
        assertThat(request.getValue().getItems().get(0).getReportSourceAttachmentIds()).containsExactly(9L);
    }

    /** multipart 同次接收 JSON、文件及原始中文文件名。 */
    @Test
    public void shouldSubmitMultipartWithOriginalChineseNames() throws Exception {
        when(service.addAdjustLog(any(), anyList(), eq("[\"股票报告.pdf\"]"))).thenReturn(new BatchStockAdjustDto());
        MockMultipartFile request = new MockMultipartFile("request", "", "application/json",
                "{\"currentUserId\":\"1\",\"stocks\":[{\"stockCode\":\"600001.SH\"}]}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "股票报告.pdf", "application/pdf", new byte[] {1});
        mockMvc.perform(multipart("/api/v1/batchStockPoolAdjust/addAdjustLogWithFiles")
                        .file(request).file(file).param("originalFileNameListJson", "[\"股票报告.pdf\"]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        ArgumentCaptor<List> files = ArgumentCaptor.forClass(List.class);
        verify(service).addAdjustLog(any(), files.capture(), eq("[\"股票报告.pdf\"]"));
        assertThat(files.getValue()).hasSize(1);
    }
}

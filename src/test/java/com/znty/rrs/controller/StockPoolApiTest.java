package com.znty.rrs.controller;

import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustAuditReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryReq;
import com.znty.rrs.entity.stockpoolquery.StockPoolQueryReq;
import com.znty.rrs.service.StockPoolAdjustFlowService;
import com.znty.rrs.service.StockPoolAdjustHistoryService;
import com.znty.rrs.service.StockPoolAdjustService;
import com.znty.rrs.service.StockPoolQueryService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 股票页面实际 HTTP 路由、请求参数与基础指标 JSON 契约。 */
public class StockPoolApiTest extends ControllerApiTestSupport {
    /** 独立控制器测试客户端，不连接真实库。 */
    private MockMvc client;
    /** 申请服务模拟组件。 */
    private StockPoolAdjustService apply;
    /** 审核服务模拟组件。 */
    private StockPoolAdjustFlowService audit;
    /** 查询服务模拟组件。 */
    private StockPoolQueryService query;
    /** 历史服务模拟组件。 */
    private StockPoolAdjustHistoryService history;

    /** 装配四个实际股票控制器。 */
    @Before public void setUp() {
        apply = mock(StockPoolAdjustService.class);
        audit = mock(StockPoolAdjustFlowService.class);
        query = mock(StockPoolQueryService.class);
        history = mock(StockPoolAdjustHistoryService.class);
        StockPoolAdjustController a = new StockPoolAdjustController();
        StockPoolAdjustFlowController b = new StockPoolAdjustFlowController();
        StockPoolQueryController c = new StockPoolQueryController();
        StockPoolAdjustHistoryController d = new StockPoolAdjustHistoryController();
        ReflectionTestUtils.setField(a, "stockPoolAdjustService", apply);
        ReflectionTestUtils.setField(b, "stockPoolAdjustFlowService", audit);
        ReflectionTestUtils.setField(c, "stockPoolQueryService", query);
        ReflectionTestUtils.setField(d, "stockPoolAdjustHistoryService", history);
        client = MockMvcBuilders.standaloneSetup(a, b, c, d).build();
    }

    /** 页面依赖的所有 JSON 接口统一 POST，并返回 ApiResponse。 */
    @Test public void pageRoutesShouldAcceptPostJson() throws Exception {
        for (String method : new String[]{"queryStockPage", "queryStockTypeList", "queryIndustryList", "queryStockDetail",
                "queryAdjustPoolList", "queryStockPoolStatus", "queryAdjustLogList", "queryAdjustStepList",
                "checkAdjust", "addAdjustLog", "submitAdjustAudit"}) {
            assertPostSuccess(client, "/api/v1/stockPoolAdjust/" + method, "{}");
        }
        for (String method : new String[]{"queryStockPoolPage", "exportStockPoolExcel", "addStockToMyPool",
                "deleteStockFromMyPool", "queryFavoritedCodeList"}) {
            assertPostSuccess(client, "/api/v1/stockPoolQuery/" + method, "{}");
        }
        for (String method : new String[]{"queryStockPoolAdjustHistoryPage", "exportStockPoolAdjustHistoryExcel", "queryIndustryList"}) {
            assertPostSuccess(client, "/api/v1/stockPoolAdjustHistory/" + method, "{}");
        }
    }

    /** 查询的交集、自然日和用户条件完整反序列化，内部截止日不可由客户端指定。 */
    @Test public void queryFiltersShouldReachServiceUnchanged() throws Exception {
        assertPostSuccess(client, "/api/v1/stockPoolQuery/queryStockPoolPage",
                "{\"poolIds\":[1,2],\"stockCode\":\"600001\",\"entryTimeStart\":\"2026-10-01\",\"entryTimeEnd\":\"2026-10-08\",\"adjusterName\":\"研究员\",\"myManagedStocks\":true,\"myStocks\":true,\"currentUserId\":\"2\",\"pageIndex\":2,\"pageSize\":10,\"entryTimeEndExclusive\":\"2000-01-01T00:00:00\"}");
        ArgumentCaptor<StockPoolQueryReq> argument = ArgumentCaptor.forClass(StockPoolQueryReq.class);
        verify(query).queryStockPoolPage(argument.capture());
        StockPoolQueryReq req = argument.getValue();
        assertThat(req.getPoolIds()).containsExactly(1L, 2L);
        assertThat(req.getMyManagedStocks()).isTrue();
        assertThat(req.getMyStocks()).isTrue();
        assertThat(req.getCurrentUserId()).isEqualTo("2");
        assertThat(req.getEntryTimeEnd()).isEqualTo("2026-10-08");
        assertThat(req.getEntryTimeEndExclusive()).isNull();
        assertThat(req.getPageIndex()).isEqualTo(2);
    }

    /** 历史行业、方向、状态和日期采用股票请求参数。 */
    @Test public void historyFiltersShouldReachService() throws Exception {
        assertPostSuccess(client, "/api/v1/stockPoolAdjustHistory/queryStockPoolAdjustHistoryPage",
                "{\"poolIds\":[3],\"stockCode\":\"600001\",\"industryCode\":\"A02\",\"adjustTimeStart\":\"2026-10-01\",\"adjustTimeEnd\":\"2026-10-08\",\"adjustMode\":\"调入\",\"auditStatus\":\"20\"}");
        ArgumentCaptor<StockPoolAdjustHistoryReq> argument = ArgumentCaptor.forClass(StockPoolAdjustHistoryReq.class);
        verify(history).queryStockPoolAdjustHistoryPage(argument.capture());
        assertThat(argument.getValue().getIndustryCode()).isEqualTo("A02");
        assertThat(argument.getValue().getAuditStatus()).isEqualTo("20");
        assertThat(argument.getValue().getAdjustMode()).isEqualTo("调入");
        assertThat(argument.getValue().getAdjustTimeEnd()).isEqualTo("2026-10-08");
    }

    /** 申请及修改审核支持真实 multipart 文件和中文文件名上下文。 */
    @Test public void multipartSubmissionShouldPreserveFilesAndOriginalNames() throws Exception {
        MockMultipartFile request = new MockMultipartFile("request", "", "application/json",
                "{\"stockCode\":\"600001.SH\",\"adjusterId\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "stock.pdf", "application/pdf", new byte[]{1, 2});
        MockMultipartFile names = new MockMultipartFile("originalFileNameListJson", "", "text/plain",
                "[\"股票报告.pdf\"]".getBytes(StandardCharsets.UTF_8));
        client.perform(multipart("/api/v1/stockPoolAdjust/addAdjustLogWithFiles").file(request).file(file).file(names))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(apply).addAdjustLog(any(StockPoolAdjustSubmitReq.class), anyList(), eq("[\"股票报告.pdf\"]"));
        MockMultipartFile auditRequest = new MockMultipartFile("request", "", "application/json",
                "{\"stepId\":1,\"processAction\":\"approve\",\"handlerId\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        client.perform(multipart("/api/v1/stockPoolAdjust/submitAdjustAuditWithFiles").file(auditRequest).file(file).file(names))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(audit).submitAdjustAudit(any(StockPoolAdjustAuditReq.class), anyList(), eq("[\"股票报告.pdf\"]"));
    }

    /** JavaBean 的 A 股字段必须保持前端实际使用的 aShareMarketValue，零值不能变空。 */
    @Test public void stockMetricsShouldKeepJsonFieldNamesAndZero() throws Exception {
        StockInfoDto stock = new StockInfoDto();
        stock.setAShareMarketValue(new BigDecimal("123.5"));
        stock.setPreviousClosePrice(BigDecimal.ZERO);
        when(apply.queryStockDetail(any())).thenReturn(stock);
        client.perform(post("/api/v1/stockPoolAdjust/queryStockDetail").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.aShareMarketValue").value(123.5))
                .andExpect(jsonPath("$.data.previousClosePrice").value(0));
    }
}

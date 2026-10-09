package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustDto;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckReq;
import com.znty.rrs.entity.stockpooladjust.StockAdjustSubmitDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchStockPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证股票批量权限、完整分组、一般流程及共享附件编排 */
public class BatchStockPoolAdjustServiceTest {
    /** 批量业务服务 */
    private BatchStockPoolAdjustService service;
    /** 股票单笔业务服务 */
    private StockPoolAdjustService stocks;
    /** 批量查询组件 */
    private BatchStockPoolAdjustMapper mapper;
    /** 投资池权限组件 */
    private InvestmentPoolMapper pools;
    /** 共用附件服务 */
    private SysAttachmentService attachments;

    /** 构造可控的单笔结果和管理员目标池。 */
    @Before
    public void setUp() {
        service = new BatchStockPoolAdjustService();
        stocks = mock(StockPoolAdjustService.class);
        mapper = mock(BatchStockPoolAdjustMapper.class);
        pools = mock(InvestmentPoolMapper.class);
        attachments = mock(SysAttachmentService.class);
        InvestmentPoolService paths = mock(InvestmentPoolService.class);
        when(paths.queryPoolFullNameMap()).thenReturn(Collections.singletonMap(10L, "股票池"));
        ReflectionTestUtils.setField(service, "batchStockPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
        ReflectionTestUtils.setField(service, "investmentPoolService", paths);
        ReflectionTestUtils.setField(service, "stockPoolAdjustService", stocks);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachments);
        when(mapper.queryEnabledStockLeafPoolCount(10L)).thenReturn(1);
        when(stocks.checkAdjust(any())).thenAnswer(call -> {
            StockAdjustCheckReq req = call.getArgument(0);
            StockAdjustCheckDto dto = new StockAdjustCheckDto();
            // 为每只股票创建独立结果，避免不同组共享可变明细
            dto.setItems(new ArrayList<>(Collections.singletonList(row(req.getStockCode(), 10L, "manual", "调入", true))));
            return dto;
        });
        when(stocks.addBatchAdjustLogList(anyList(), any())).thenAnswer(call -> {
            List<StockPoolAdjustSubmitReq> requests = call.getArgument(0);
            List<StockAdjustSubmitDto> results = new ArrayList<>();
            for (int index = 0; index < requests.size(); index++) {
                StockAdjustSubmitDto result = new StockAdjustSubmitDto();
                result.setAdjustLogIds(Collections.singletonList((long) index + 1));
                result.setAdjustBatchNos(Collections.singletonList("STOCK-" + index));
                results.add(result);
            }
            return results;
        });
    }

    /** 清理模拟 Mapper 未消费的分页线程状态。 */
    @After
    public void tearDown() { PageHelper.clearPage(); }

    /** 校验逐股票传用户和方向，结果仅保留一般流程。 */
    @Test
    public void checkShouldKeepIndependentGroupsAndOnlyNormalFlows() {
        BatchStockAdjustDto result = service.checkAdjust(checkRequest("600001.SH", "600002.SH"));
        assertThat(result.getItems()).extracting(StockAdjustCheckDto.CheckResultItem::getAdjustGroupKey)
                .containsExactly("600001.SH_stock-group-1", "600002.SH_stock-group-1");
        assertThat(result.getStockCount()).isEqualTo(2);
        assertThat(result.getItems().get(0).getFlowOptions()).extracting(StockAdjustCheckDto.FlowOption::getFlowType)
                .containsExactly("normalInbound");
        ArgumentCaptor<StockAdjustCheckReq> requests = ArgumentCaptor.forClass(StockAdjustCheckReq.class);
        verify(stocks, times(2)).checkAdjust(requests.capture());
        assertThat(requests.getAllValues()).extracting(StockAdjustCheckReq::getCurrentUserId).containsOnly("1");
        assertThat(requests.getValue().getItems().get(0).getAdjustMode()).isEqualTo("调入");
    }

    /** 一只股票失效不遮蔽其他股票，关系项失败必须阻断整组。 */
    @Test
    public void checkShouldIsolateFailedStocksAndCompleteRelationGroups() {
        doAnswer(call -> {
            StockAdjustCheckReq req = call.getArgument(0);
            if ("600001.SH".equals(req.getStockCode())) { throw new BizException("股票已退市"); }
            StockAdjustCheckDto dto = new StockAdjustCheckDto();
            // 同一股票的失败联动项必须连同主项隔离
            dto.setItems(Arrays.asList(row(req.getStockCode(), 10L, "manual", "调入", true),
                    row(req.getStockCode(), 20L, "linkage", "调入", false)));
            return dto;
        }).when(stocks).checkAdjust(any());
        BatchStockAdjustDto result = service.checkAdjust(checkRequest("600001.SH", "600002.SH"));
        assertThat(result.getItems()).hasSize(3).extracting(StockAdjustCheckDto.CheckResultItem::isCanAdjust).containsOnly(false);
        assertThat(result.getItems().get(0).getFailReasons()).contains("股票已退市");
        assertThat(result.getItems().get(1).getFailReasons()).contains("同一股票调库分组存在未通过的调整项");
        assertThat(result.getItems().get(1).getFlowOptions().get(0).isSelectable()).isFalse();
    }

    /** 仅快速流程可用时不能作为批量候选组提交。 */
    @Test
    public void checkShouldBlockPoolWithoutUsableNormalFlow() {
        StockAdjustCheckDto dto = new StockAdjustCheckDto();
        // 保留单笔合法快速候选，验证批量主动阻断
        StockAdjustCheckDto.CheckResultItem row = row("600001.SH", 10L, "manual", "调入", true);
        row.getFlowOptions().get(0).setSelectable(false);
        dto.setItems(Collections.singletonList(row));
        doReturn(dto).when(stocks).checkAdjust(any());
        BatchStockAdjustDto result = service.checkAdjust(checkRequest("600001.SH"));
        assertThat(result.getItems().get(0).isCanAdjust()).isFalse();
        assertThat(result.getItems().get(0).getFailReasons()).contains("目标投资池未配置可用的一般审批流程");
    }

    /** 同代码大小写不同仍视为重复选择。 */
    @Test
    public void checkShouldRejectDuplicateStockCodes() {
        // 构造大小写不同的重复代码
        assertThatThrownBy(() -> service.checkAdjust(checkRequest("600001.SH", "600001.sh")))
                .hasMessageContaining("重复选择股票");
    }

    /** 关系项继承主项流程，不要求关系池自身另配一般流程。 */
    @Test
    public void checkShouldKeepRelationsWhenOnlyManualPoolHasNormalFlow() {
        // 构造一般流程主项和只有快速候选的反向互斥项
        StockAdjustCheckDto dto = new StockAdjustCheckDto();
        StockAdjustCheckDto.CheckResultItem manual = row("600001.SH", 10L, "manual", "调入", true);
        StockAdjustCheckDto.CheckResultItem mutex = row("600001.SH", 20L, "mutex", "调出", true);
        mutex.setFlowOptions(Collections.singletonList(mutex.getFlowOptions().get(1)));
        dto.setItems(Arrays.asList(manual, mutex));
        doReturn(dto).when(stocks).checkAdjust(any());
        // 关系项仍保留反向方向与完整股票组，提交时继承主项流程
        BatchStockAdjustDto result = service.checkAdjust(checkRequest("600001.SH"));
        assertThat(result.getItems()).hasSize(2).extracting(StockAdjustCheckDto.CheckResultItem::isCanAdjust)
                .containsOnly(true);
        assertThat(result.getItems().get(1).getFlowOptions()).isEmpty();
        assertThat(result.getItems().get(1).getAdjustMode()).isEqualTo("调出");
    }

    /** 查询无权限时返回空池页并阻止股票候选查询。 */
    @Test
    public void queriesShouldRejectMissingAdjustablePermission() {
        BatchStockPoolAdjustReq req = new BatchStockPoolAdjustReq();
        req.setCurrentUserId("2");
        assertThat(service.queryPoolPage(req).getRecords()).isEmpty();
        req.setPoolId(10L); req.setDirection("in");
        assertThatThrownBy(() -> service.queryStockPage(req)).hasMessageContaining("无权调整");
        verify(mapper, never()).queryStockPage(any());
    }

    /** 角色授权可查询，股票预留管理员 ID 段直接放行。 */
    @Test
    public void queriesShouldUseRolePermissionAndReservedAdminRange() {
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(10L); permission.setHandlerId(8L); permission.setHandlerType("role");
        when(pools.queryUserRoleIdList(2L)).thenReturn(Collections.singletonList(8L));
        when(pools.queryPermissionListByType("adjustable")).thenReturn(Collections.singletonList(permission));
        BatchStockPoolAdjustReq req = new BatchStockPoolAdjustReq();
        req.setPoolId(10L); req.setDirection("out"); req.setCurrentUserId("2");
        service.queryStockPage(req);
        PageHelper.clearPage();
        req.setCurrentUserId("10000");
        service.queryStockPage(req);
        verify(mapper, times(2)).queryStockPage(any());
    }

    /** 方向和启用股票叶子池均为查询、校验的硬边界。 */
    @Test
    public void checkShouldRejectInvalidPoolAndDirection() {
        // 默认请求只包含一只股票
        BatchStockAdjustReq req = checkRequest("600001.SH");
        req.setPoolId(20L);
        assertThatThrownBy(() -> service.checkAdjust(req)).hasMessageContaining("支持股票的叶子池");
        req.setPoolId(10L); req.setDirection("invalid");
        assertThatThrownBy(() -> service.checkAdjust(req)).hasMessageContaining("in 或 out");
    }

    /** 提交保留反向互斥和各股票报告引用，整批原因、建议复制到独立请求。 */
    @Test
    @SuppressWarnings("unchecked")
    public void submitShouldKeepOppositeMutexAndStockReportOwnership() {
        // 构造两只股票及第一只股票的互斥调出项
        BatchStockAdjustReq req = submitRequest("600001.SH", "600002.SH");
        BatchStockAdjustReq.AdjustItem mutex = item("600001.SH", 20L, "mutex", "调出");
        req.getItems().add(mutex);
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(11L));
        req.getItems().get(1).setReportSourceAttachmentIds(Collections.singletonList(22L));
        BatchStockAdjustDto result = service.addAdjustLog(req);
        assertThat(result.getStockCount()).isEqualTo(2);
        assertThat(result.getAdjustBatchNos()).containsExactly("STOCK-0", "STOCK-1");
        ArgumentCaptor<List<StockPoolAdjustSubmitReq>> requests = ArgumentCaptor.forClass(List.class);
        verify(stocks).addBatchAdjustLogList(requests.capture(), eq(null));
        assertThat(requests.getValue().get(0).getItems()).hasSize(2)
                .extracting(StockPoolAdjustSubmitReq.AdjustItem::getAdjustMode).containsExactly("调入", "调出");
        assertThat(requests.getValue().get(0).getItems().get(0).getReportSourceAttachmentIds()).containsExactly(11L);
        assertThat(requests.getValue().get(1).getItems().get(0).getReportSourceAttachmentIds()).containsExactly(22L);
        assertThat(requests.getValue()).extracting(StockPoolAdjustSubmitReq::getAdjustType).containsOnly("手动批量调整");
        assertThat(requests.getValue()).extracting(StockPoolAdjustSubmitReq::getAdjustReason).containsOnly("批量原因");
    }

    /** 快速及批量流程伪造请求在写入前拒绝。 */
    @Test
    public void submitShouldRejectFastAndBatchFlows() {
        // 构造合法主项后替换客户端流程类型
        BatchStockAdjustReq req = submitRequest("600001.SH");
        req.getItems().get(0).setFlowType("fastInbound");
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("一般审批流程");
        req.getItems().get(0).setFlowType("batchInbound");
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("一般审批流程");
        verify(stocks, never()).addBatchAdjustLogList(anyList(), any());
    }

    /** 错误主项目标、方向、分组及多主项均拒绝。 */
    @Test
    public void submitShouldRejectForgedManualContextAndGroups() {
        // 按同一有效请求依次构造四类明细伪造
        BatchStockAdjustReq req = submitRequest("600001.SH");
        req.getItems().get(0).setTargetPoolId(20L);
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("目标池或方向");
        req.getItems().get(0).setTargetPoolId(10L); req.getItems().get(0).setAdjustMode("调出");
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("目标池或方向");
        req.getItems().get(0).setAdjustMode("调入"); req.getItems().get(0).setAdjustGroupKey("other-group");
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("分组标识");
        req.getItems().get(0).setAdjustGroupKey("600001.SH_stock-group-1");
        // 添加第二条主项验证一股票一主项约束
        req.getItems().add(item("600001.SH", 10L, "manual", "调入"));
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("仅包含一条手工");
    }

    /** 提交身份不得通过调整人字段冒用另一用户。 */
    @Test
    public void submitShouldRequireSameCurrentUserAndAdjuster() {
        // 在有效批量请求上伪造调整人
        BatchStockAdjustReq req = submitRequest("600001.SH");
        req.setAdjusterId("2");
        assertThatThrownBy(() -> service.addAdjustLog(req)).hasMessageContaining("必须与当前用户一致");
    }

    /** 原始文件名和共用上传上下文只创建一次并送入多单入口。 */
    @Test
    public void multipartShouldReuseSharedSubmissionFilesAndChineseNames() {
        // 准备一份整批上传报告和共享上下文
        BatchStockAdjustReq req = submitRequest("600001.SH", "600002.SH");
        List<MultipartFile> files = Collections.singletonList(new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[] {1}));
        List<String> names = Collections.singletonList("股票报告.pdf");
        SysAttachmentService.SubmissionFiles shared = new SysAttachmentService().createSharedSubmissionFiles(files, "1", names);
        when(attachments.parseOriginalFileNameListJson("[\"股票报告.pdf\"]")).thenReturn(names);
        when(attachments.createSharedSubmissionFiles(files, "1", names)).thenReturn(shared);
        service.addAdjustLog(req, files, "[\"股票报告.pdf\"]");
        verify(attachments).createSharedSubmissionFiles(files, "1", names);
        verify(stocks).addBatchAdjustLogList(anyList(), eq(shared));
    }

    /** 构造股票批量校验请求。 */
    private BatchStockAdjustReq checkRequest(String... codes) {
        BatchStockAdjustReq req = new BatchStockAdjustReq();
        req.setCurrentUserId("1"); req.setPoolId(10L); req.setDirection("in");
        List<BatchStockAdjustReq.StockItem> selected = new ArrayList<>();
        for (String code : codes) {
            BatchStockAdjustReq.StockItem item = new BatchStockAdjustReq.StockItem();
            item.setStockCode(code); selected.add(item);
        }
        req.setStocks(selected);
        return req;
    }

    /** 构造股票批量提交请求。 */
    private BatchStockAdjustReq submitRequest(String... codes) {
        // 复用批量上下文，逐股添加完整主项
        BatchStockAdjustReq req = checkRequest(codes);
        req.setAdjusterId("1"); req.setAdjusterName("管理员"); req.setAdjustReason("批量原因"); req.setAdjustAdvice("批量建议");
        List<BatchStockAdjustReq.AdjustItem> items = new ArrayList<>();
        for (String code : codes) {
            // 为当前股票构造一条手工调入项
            items.add(item(code, 10L, "manual", "调入"));
        }
        req.setItems(items);
        return req;
    }

    /** 构造单条完整提交明细。 */
    private BatchStockAdjustReq.AdjustItem item(String code, Long poolId, String tag, String mode) {
        BatchStockAdjustReq.AdjustItem item = new BatchStockAdjustReq.AdjustItem();
        item.setStockCode(code); item.setTargetPoolId(poolId); item.setItemTag(tag); item.setAdjustMode(mode);
        item.setAdjustGroupKey(code + "_stock-group-1"); item.setFlowId(100L); item.setFlowKey("stock-normal");
        item.setFlowType("调入".equals(mode) ? "normalInbound" : "normalOutbound");
        return item;
    }

    /** 构造含一般和快速流程的单笔校验明细。 */
    private StockAdjustCheckDto.CheckResultItem row(String code, Long pool, String tag, String mode, boolean valid) {
        StockAdjustCheckDto.CheckResultItem row = new StockAdjustCheckDto.CheckResultItem();
        row.setStockCode(code); row.setTargetPoolId(pool); row.setItemTag(tag); row.setAdjustMode(mode);
        row.setAdjustGroupKey("stock-group-1"); row.setCanAdjust(valid); row.setWarnings(Collections.singletonList("弹性限制提示"));
        row.setFailReasons(valid ? Collections.emptyList() : Collections.singletonList("当前用户没有投资池调整权限"));
        StockAdjustCheckDto.FlowOption normal = new StockAdjustCheckDto.FlowOption();
        normal.setFlowType("调入".equals(mode) ? "normalInbound" : "normalOutbound"); normal.setSelectable(valid); normal.setRecommended(true);
        StockAdjustCheckDto.FlowOption fast = new StockAdjustCheckDto.FlowOption();
        fast.setFlowType("调入".equals(mode) ? "fastInbound" : "fastOutbound"); fast.setSelectable(valid);
        row.setFlowOptions(Arrays.asList(normal, fast));
        return row;
    }
}

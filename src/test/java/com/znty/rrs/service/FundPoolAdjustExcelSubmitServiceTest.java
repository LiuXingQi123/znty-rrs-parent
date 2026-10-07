package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.FundPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金 Excel 导入复核、来源分组提交与事务回滚测试。 */
public class FundPoolAdjustExcelSubmitServiceTest {
    /** 待测试基金调库服务 */
    private FundPoolAdjustService service;
    /** 基金调库数据访问组件 */
    private FundPoolAdjustMapper mapper;
    /** 投资池数据访问组件 */
    private InvestmentPoolMapper poolMapper;
    /** 一般审批流程数据访问组件 */
    private FlowMapper flowMapper;
    /** 附件服务 */
    private SysAttachmentService attachmentService;
    /** 基金主档 */
    private FundInfoBo fund;
    /** 无报告限制的第一个投资池 */
    private InvestmentPoolBo firstPool;
    /** 无报告限制的第二个投资池 */
    private InvestmentPoolBo secondPool;

    /** 初始化真实一般流程、基金主档与两个可调入投资池。 */
    @Before
    public void setUp() {
        service = new FundPoolAdjustService();
        mapper = mock(FundPoolAdjustMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        flowMapper = mock(FlowMapper.class);
        attachmentService = mock(SysAttachmentService.class);
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        fund = new FundInfoBo();
        fund.setFundCode("FUND001.SH");
        fund.setFundName("主档基金全称");
        fund.setFundShortName("主档基金简称");
        fund.setSecurityType("bond_fund");
        fund.setSecurityStatus("A");
        fund.setMarketCode("SH");
        // 构造目标池及其一般审批流程配置
        firstPool = buildPool(10L, "第一基金池");
        // 构造同一基金另一来源组使用的目标池
        secondPool = buildPool(20L, "第二基金池");
        when(mapper.queryFundByCode("FUND001.SH")).thenReturn(fund);
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(firstPool, secondPool));
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.emptyList());
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        AtomicLong logSequence = new AtomicLong();
        when(mapper.addAdjustLog(any())).thenAnswer(invocation -> {
            FundAdjustLogBo log = invocation.getArgument(0);
            log.setId(logSequence.incrementAndGet());
            return 1;
        });
        // 配置已发布、具有开始节点及人工审批节点的一般流程
        configureNormalFlow();
    }

    /** 释放导入分页线程上下文，避免影响同线程后续用例。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 串联两个真实服务，API 调入值须转换为基金内部枚举值。 */
    @Test
    public void actualImportAndFundServicesShouldSubmitInboundApproval() throws Exception {
        // 使用真实上传、校验及整批基金提交链路验证方向和状态
        assertActualImportSubmission("in", AdjustMode.IN.getCode());
    }

    /** 调出走同一真实链路，不能被 in/out 与中文内部编码差异阻断。 */
    @Test
    public void actualImportAndFundServicesShouldSubmitOutboundApproval() throws Exception {
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.singletonList(10L));
        // 验证真实调出校验返回 API 编码，写入基金日志使用内部编码
        assertActualImportSubmission("out", AdjustMode.OUT.getCode());
    }

    /**
     * 构造仅替代持久化的导入环境，基金规则与提交服务均使用真实实现。
     *
     * @param direction 导入 API 使用的 in 或 out 方向
     * @param internalMode 预期写入基金日志的内部调整方向编码
     */
    private void assertActualImportSubmission(String direction, String internalMode) throws Exception {
        FundPoolExcelImportMapper importMapper = mock(FundPoolExcelImportMapper.class);
        AtomicReference<SysImpTmpBo> batch = new AtomicReference<>();
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        when(importMapper.addBatch(any())).thenAnswer(invocation -> {
            batch.set(invocation.getArgument(0));
            return 1;
        });
        when(importMapper.addItemList(anyList())).thenAnswer(invocation -> {
            List<SysImpTmpDetlBo> added = invocation.getArgument(0);
            for (SysImpTmpDetlBo row : added) {
                row.setId((long) rows.size() + 1);
                rows.add(row);
            }
            return added.size();
        });
        when(importMapper.queryBatchByImpId(anyString())).thenAnswer(invocation -> batch.get());
        when(importMapper.queryBatchIdForUpdate(anyString())).thenReturn(1L);
        when(importMapper.queryBatchItemList(anyString())).thenReturn(rows);
        when(importMapper.queryItemPage(anyString(), any(), any())).thenReturn(rows);
        when(importMapper.queryEnabledLeafPoolList("基金库", "第一基金池"))
                .thenReturn(Collections.singletonList(firstPool));
        FundPoolExcelImportService importService = new FundPoolExcelImportService();
        ReflectionTestUtils.setField(importService, "fundPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(importService, "fundPoolAdjustService", service);
        ReflectionTestUtils.setField(importService, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(importService, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(importService, "sysAttachmentService", new SysAttachmentService());
        FundPoolExcelImportReq req = new FundPoolExcelImportReq();
        req.setDirection(direction);
        req.setCurrentUserId("1");
        req.setCurrentUserName("测试用户");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            String[] headers = {"父池名称", "子池名称", "基金名称", "基金代码", "基金评分", "基金投资类型", "风管领导审批"};
            String[] values = {"基金库", "第一基金池", "上传名称", "FUND001.SH", "0", "stock", "0"};
            workbook.getSheetAt(0).createRow(0);
            workbook.getSheetAt(0).createRow(1);
            for (int column = 0; column < headers.length; column++) {
                workbook.getSheetAt(0).getRow(0).createCell(column).setCellValue(headers[column]);
                workbook.getSheetAt(0).getRow(1).createCell(column).setCellValue(values[column]);
            }
            workbook.write(output);
            // 真实解析七列文件并锁定批次参数
            importService.uploadExcel(req, new MockMultipartFile("file", "基金.xlsx",
                    "application/octet-stream", output.toByteArray()), null);
        }
        FundPoolExcelImportDto checked = importService.checkImport(req);
        assertThat(checked.getCheckItems()).hasSize(1);
        assertThat(checked.getCheckItems().get(0).getFailReasons()).isEmpty();
        assertThat(checked.getCheckItems().get(0).getAdjustDirection()).isEqualTo(direction);
        FundPoolExcelImportDto submitted = importService.submitImport(req);
        assertThat(submitted.getSaveRslt()).isEqualTo("1");
        ArgumentCaptor<FundAdjustLogBo> log = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper).addAdjustLog(log.capture());
        assertThat(log.getValue().getAdjustMode()).isEqualTo(internalMode);
        assertThat(log.getValue().getAuditStatus()).isEqualTo("00");
        assertThat(log.getValue().getFundScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(log.getValue().getNeedRiskLeaderApproval()).isZero();
        verify(mapper, never()).addFundPoolStatus(any());
        verify(mapper, never()).deleteFundPoolStatus(any(), any());
    }

    /** 无报告限制时通过完整基金校验，并仅返回目标池的一般流程。 */
    @Test
    public void excelCheckShouldUseMasterDataAndNormalFlow() {
        // 构造含伪造池名称的 Excel 主项校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());
        req.getItems().get(0).setTargetPoolName("伪造池名称");

        FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

        assertThat(item.isCanAdjust()).isTrue();
        assertThat(item.getFundCode()).isEqualTo(fund.getFundCode());
        assertThat(item.getFundShortName()).isEqualTo("主档基金简称");
        assertThat(item.getSecurityType()).isEqualTo("bond_fund");
        assertThat(item.getPoolName()).isEqualTo("第一基金池");
        assertThat(item.getFlowOptions()).hasSize(1);
        assertThat(item.getFlowOptions().get(0).getFlowType()).isEqualTo("normalInbound");
        assertThat(item.getFlowOptions().get(0).isSelectable()).isTrue();
        verify(mapper, never()).addAdjustLog(any());
    }

    /** any 与 internal 报告限制均在 Excel 主项校验阶段明确阻断。 */
    @Test
    public void excelCheckShouldRejectAnyAndInternalReports() {
        for (String restriction : Arrays.asList("any", "internal")) {
            firstPool.setInReportRestriction(restriction);
            // 构造无报告附件的调入校验请求
            FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());

            FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("要求提交基金报告")
                    && reason.contains("Excel 导入暂不支持报告附件") && reason.contains("单笔调库"));
            assertThat(item.getFlowOptions().get(0).isSelectable()).isFalse();
        }
    }

    /** 调出报告限制与调入保持一致，清空主项也不能绕过报告要求。 */
    @Test
    public void excelCheckShouldApplyOutboundReportRestriction() {
        firstPool.setOutReportRestriction("internal");
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.singletonList(10L));
        // 构造清空组使用的调出主项校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.OUT.getCode());

        FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

        assertThat(item.isCanAdjust()).isFalse();
        assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("Excel 导入暂不支持报告附件"));
    }

    /** 关系项的报告口径与单笔申请一致，仍独立检查关系池调整权限。 */
    @Test
    public void excelCheckShouldKeepRelationReportRuleAndCheckEveryPoolPermission() {
        secondPool.setInReportRestriction("internal");
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(10L);
        relation.setRelationPoolId(20L);
        relation.setRelationType("in_linked");
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(10L);
        permission.setHandlerId(2L);
        permission.setHandlerType("user");
        when(poolMapper.queryPermissionListByType("adjustable")).thenReturn(Collections.singletonList(permission));
        // 构造主池可调整而关系池无权限的校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());

        List<FundAdjustCheckDto.CheckResultItem> items = service.checkExcelImportAdjust(req, "2").getItems();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).isCanAdjust()).isTrue();
        assertThat(items.get(1).getItemTag()).isEqualTo("linkage");
        assertThat(items.get(1).isCanAdjust()).isFalse();
        assertThat(items.get(1).getFailReasons()).containsExactly("当前用户没有投资池调整权限：第二基金池");
    }

    /** 同一基金在不同池的来源组分别保留评分、投资类型和领导审批字段。 */
    @Test
    public void excelSubmitShouldPreserveIndependentSourceFieldsAndLeavePoolStatusUnchanged() {
        // 构造同一基金在不同池的独立来源组
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造需要独立保留三字段的第二个来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");
        first.setFundScore(new BigDecimal("-12.5"));
        first.setFundInvestmentType("stock");
        first.setNeedRiskLeaderApproval(0);
        second.setFundScore(new BigDecimal("999999.99"));
        second.setFundInvestmentType("bond_hybrid");
        second.setNeedRiskLeaderApproval(1);

        List<FundAdjustSubmitDto> results = service.addExcelImportAdjustLogList(Arrays.asList(first, second));

        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundScore)
                .containsExactly(new BigDecimal("-12.5"), new BigDecimal("999999.99"));
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundInvestmentType)
                .containsExactly("stock", "bond_hybrid");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getNeedRiskLeaderApproval).containsExactly(0, 1);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getAuditStatus).containsOnly("00");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundName).containsOnly("主档基金全称");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getSecurityType).containsOnly("bond_fund");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFlowType).containsOnly("normalInbound");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAdjustBatchNos().get(0)).startsWith("FUND-")
                .isNotEqualTo(results.get(1).getAdjustBatchNos().get(0));
        verify(mapper, times(4)).addAdjustStep(any());
        verify(mapper, never()).addFundPoolStatus(any());
        verify(mapper, never()).deleteFundPoolStatus(any(), any());
    }

    /** 所有来源组的待办与重复申请检查均发生于首次写入之前。 */
    @Test
    public void excelSubmitShouldFinishAllSourceChecksBeforeWriting() {
        // 构造两个独立来源组以验证前置复核顺序
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造必须在首次写入前完成复核的后续来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

        service.addExcelImportAdjustLogList(Arrays.asList(first, second));

        InOrder order = inOrder(mapper);
        order.verify(mapper).queryRecentDuplicate("FUND001.SH", 10L, AdjustMode.IN.getCode(), "1");
        order.verify(mapper).queryFundHasPendingProcess("FUND001.SH", 20L, null);
        order.verify(mapper).queryRecentDuplicate("FUND001.SH", 20L, AdjustMode.IN.getCode(), "1");
        order.verify(mapper).addAdjustLog(argThat(log -> Long.valueOf(10L).equals(log.getTargetPoolId())));
    }

    /** 同组关系项复制三字段并复用主流程，只有主项创建审批步骤。 */
    @Test
    public void excelSubmitShouldCopySourceFieldsToRelationItems() {
        secondPool.setInReportRestriction("internal");
        // 构造一个主项与同组关系项的来源组
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.setFundScore(new BigDecimal("7.25"));
        req.setFundInvestmentType("money_market");
        req.setNeedRiskLeaderApproval(1);
        FundPoolAdjustSubmitReq.AdjustItem relation = new FundPoolAdjustSubmitReq.AdjustItem();
        relation.setTargetPoolId(20L);
        relation.setAdjustMode(AdjustMode.IN.getCode());
        relation.setItemTag("linkage");
        relation.setAdjustGroupKey("excel-row-2");
        req.setItems(Arrays.asList(req.getItems().get(0), relation));

        FundAdjustSubmitDto result = service.addExcelImportAdjustLogList(Collections.singletonList(req)).get(0);

        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundScore)
                .containsOnly(new BigDecimal("7.25"));
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundInvestmentType).containsOnly("money_market");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getNeedRiskLeaderApproval).containsOnly(1);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFlowId).containsOnly(100L);
        assertThat(result.getAdjustBatchNos()).hasSize(1);
        verify(mapper, times(2)).addAdjustStep(any());
    }

    /** 批量或快速流程伪造为选择项时仍由一般流程复核拒绝。 */
    @Test
    public void excelSubmitShouldRejectBatchFlowSelection() {
        // 构造伪造为批量调入流程的主项
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setFlowType("batchInbound");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("只能选择目标投资池配置的一般审批流程");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 后续来源组的报告校验失败时，前面的合格分组也没有写入。 */
    @Test
    public void excelSubmitShouldWriteNothingWhenLaterSourceFails() {
        secondPool.setInReportRestriction("any");
        // 构造后续组缺少必填报告的整批申请
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造目标池需要报告但没有附件的后续来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                .isInstanceOf(BizException.class).hasMessageContaining("要求提交基金报告");

        verify(mapper, never()).addAdjustLog(any());
        verify(mapper, never()).addAdjustStep(any());
    }

    /** 跨来源组的同基金同池，无论方向，均给出可定位冲突。 */
    @Test
    public void excelSubmitShouldRejectCrossSourceDuplicatePoolInEitherDirection() {
        for (String direction : Arrays.asList(AdjustMode.IN.getCode(), AdjustMode.OUT.getCode())) {
            // 构造相同基金目标池、不同来源组的重复调整
            FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
            // 构造同目标池的另一来源组以验证任意方向冲突
            FundPoolAdjustSubmitReq second = buildSubmitRequest(10L, "excel-row-8");
            second.getItems().get(0).setAdjustMode(direction);

            assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                    .isInstanceOf(BizException.class).hasMessageContaining("来源分组冲突")
                    .hasMessageContaining("FUND001.SH").hasMessageContaining("目标池 ID 10")
                    .hasMessageContaining("excel-row-2").hasMessageContaining("excel-row-8");
        }
        verify(mapper, never()).addAdjustLog(any());
    }

    /** Excel 内部入口禁止伪造报告来源或上传索引绕过无附件约束。 */
    @Test
    public void excelSubmitShouldRejectAttachmentSpoofing() {
        // 构造伪造报告附件来源的内部请求
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(99L));

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("Excel 导入暂不支持报告或材料附件");

        req.getItems().get(0).setReportSourceAttachmentIds(Collections.emptyList());
        req.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("Excel 导入暂不支持报告或材料附件");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 提交时再次检查调整权限，不信任之前可调整的校验结果。 */
    @Test
    public void excelSubmitShouldRecheckAdjustmentPermission() {
        // 构造无调整权限用户的提交请求
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.setAdjusterId("2");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("没有投资池调整权限");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 校验和提交均重新读取基金主档，主档不存在时不得继续。 */
    @Test
    public void bothExcelEntrypointsShouldRequireCurrentFundMaster() {
        when(mapper.queryFundByCode("FUND001.SH")).thenReturn(null);
        // 构造同一不存在基金的校验及提交请求
        FundAdjustCheckReq check = buildCheckRequest(10L, AdjustMode.IN.getCode());
        // 构造提交入口需要重新读取主档的来源组
        FundPoolAdjustSubmitReq submit = buildSubmitRequest(10L, "excel-row-2");

        assertThatThrownBy(() -> service.checkExcelImportAdjust(check, "1"))
                .isInstanceOf(BizException.class).hasMessage("基金不存在或已删除");
        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(submit)))
                .isInstanceOf(BizException.class).hasMessage("基金不存在或已删除");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 单笔入口继续允许有效内部报告，并沿用主档及基金附件绑定。 */
    @Test
    public void singleSubmitShouldKeepInternalReportAndMasterDataBehavior() {
        firstPool.setInReportRestriction("internal");
        // 构造选择内部报告的原单笔基金调库申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "fund-group-1");
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(99L));

        FundAdjustSubmitDto result = service.addAdjustLog(req);

        assertThat(result.getAdjustLogIds()).containsExactly(1L);
        verify(attachmentService).validateCreditReportSources(Collections.singletonList(99L), true);
        verify(attachmentService).copyReportAttachments("ip_adjust_log_fund", 1L,
                Collections.singletonList(99L), "credit_report", "1");
        ArgumentCaptor<FundAdjustLogBo> log = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper).addAdjustLog(log.capture());
        assertThat(log.getValue().getFundName()).isEqualTo("主档基金全称");
        assertThat(log.getValue().getAuditStatus()).isEqualTo("00");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 关系项先于缺失主项提交时，准备阶段阻断且没有任何日志写入。 */
    @Test
    public void excelSubmitShouldRequireManualItemBeforeAnyWrite() {
        // 构造只有关系项、没有手工主项的错误来源组
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setItemTag("linkage");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessage("每个调库分组必须包含一条手工调整项");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 后续组的步骤持久化异常时，真实数据库事务回滚前面全部日志和步骤。 */
    @Test
    public void excelSubmitShouldRollbackWholeBatchWhenLaterPersistenceFails() {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE test_fund_log (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE test_fund_step (log_id BIGINT)");
            AtomicLong logSequence = new AtomicLong();
            doAnswer(invocation -> {
                FundAdjustLogBo log = invocation.getArgument(0);
                log.setId(logSequence.incrementAndGet());
                return jdbc.update("INSERT INTO test_fund_log (id) VALUES (?)", log.getId());
            }).when(mapper).addAdjustLog(any());
            doAnswer(invocation -> {
                FundAdjustStepBo step = invocation.getArgument(0);
                if (Long.valueOf(2L).equals(step.getAdjustLogId())) {
                    throw new BizException("模拟后续组步骤写入失败");
                }
                return jdbc.update("INSERT INTO test_fund_step (log_id) VALUES (?)", step.getAdjustLogId());
            }).when(mapper).addAdjustStep(any());
            TransactionInterceptor interceptor = new TransactionInterceptor(
                    new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource());
            ProxyFactory proxyFactory = new ProxyFactory(service);
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAdvice(interceptor);
            FundPoolAdjustService transactionalService = (FundPoolAdjustService) proxyFactory.getProxy();
            // 构造后续组在持久化阶段失败的整批请求
            FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
            // 构造触发步骤写入失败并应回滚前组的后续来源组
            FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

            assertThatThrownBy(() -> transactionalService.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                    .isInstanceOf(BizException.class).hasMessage("模拟后续组步骤写入失败");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_log", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_step", Integer.class)).isZero();
            verify(mapper, never()).addFundPoolStatus(any());
        } finally {
            database.shutdown();
        }
    }

    /**
     * 构造具有基金准入及一般调入调出流程的投资池。
     *
     * @param poolId 目标池 ID
     * @param poolName 目标池名称
     */
    private InvestmentPoolBo buildPool(Long poolId, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(poolId);
        pool.setPoolName(poolName);
        pool.setPoolType("fund");
        pool.setStatus("enabled");
        pool.setVarietyCodes("[\"fund\"]");
        pool.setMarketCodes("[\"SH\"]");
        pool.setInFlowId(100L);
        pool.setOutFlowId(100L);
        pool.setInFlowKey("fund-normal");
        pool.setOutFlowKey("fund-normal");
        pool.setInFlowName("基金一般流程");
        pool.setOutFlowName("基金一般流程");
        pool.setInReportRestriction("none");
        pool.setOutReportRestriction("none");
        return pool;
    }

    /** 配置已发布并包含实际人工审批节点的一般流程快照。 */
    private void configureNormalFlow() {
        FlowDefinitionBo flow = new FlowDefinitionBo();
        flow.setId(100L);
        flow.setFlowKey("fund-normal");
        flow.setStatus("active");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1000L);
        version.setStatus("active");
        FlowNodeBo start = new FlowNodeBo();
        start.setId(1001L);
        start.setNodeType("start");
        start.setLabel("开始");
        FlowNodeBo approval = new FlowNodeBo();
        approval.setId(1002L);
        approval.setNodeType("approval");
        approval.setLabel("基金审批");
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(1001L);
        edge.setToNodeId(1002L);
        when(flowMapper.queryFlowById(100L)).thenReturn(flow);
        when(flowMapper.queryFlowVersionByFlowIdList(eq(100L), any())).thenReturn(Collections.singletonList(version));
        when(flowMapper.queryFlowNodeListByVersionId(1000L)).thenReturn(Arrays.asList(start, approval));
        when(flowMapper.queryFlowEdgeListByVersionId(1000L)).thenReturn(Collections.singletonList(edge));
    }

    /**
     * 构造单个 Excel 来源池的校验请求。
     *
     * @param poolId 来源主项的目标池 ID
     * @param adjustMode 基金内部调入或调出编码
     */
    private FundAdjustCheckReq buildCheckRequest(Long poolId, String adjustMode) {
        FundAdjustCheckReq req = new FundAdjustCheckReq();
        req.setFundCode("FUND001.SH");
        FundAdjustCheckReq.CheckItem item = new FundAdjustCheckReq.CheckItem();
        item.setTargetPoolId(poolId);
        item.setAdjustMode(adjustMode);
        req.setItems(Collections.singletonList(item));
        return req;
    }

    /**
     * 构造含必填评分、投资类型、领导审批及一般流程的来源组。
     *
     * @param poolId 来源主项的目标池 ID
     * @param groupKey 主项及其关系项共用的来源分组标识
     */
    private FundPoolAdjustSubmitReq buildSubmitRequest(Long poolId, String groupKey) {
        FundPoolAdjustSubmitReq req = new FundPoolAdjustSubmitReq();
        req.setFundCode("FUND001.SH");
        req.setFundScore(new BigDecimal("8.5"));
        req.setFundInvestmentType("stock");
        req.setNeedRiskLeaderApproval(0);
        req.setAdjusterId("1");
        req.setAdjusterName("基金研究员");
        FundPoolAdjustSubmitReq.AdjustItem item = new FundPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(poolId);
        item.setTargetPoolName("前端传入名称");
        item.setItemTag("manual");
        item.setAdjustMode(AdjustMode.IN.getCode());
        item.setAdjustGroupKey(groupKey);
        item.setFlowId(100L);
        item.setFlowKey("fund-normal");
        item.setFlowType("normalInbound");
        req.setItems(Collections.singletonList(item));
        return req;
    }
}
